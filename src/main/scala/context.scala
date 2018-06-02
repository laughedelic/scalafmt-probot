package laughedelic.scalafmt.probot

import scala.scalajs.js
import laughedelic.probot._
import laughedelic.octokit.rest._
import io.scalajs.nodejs.buffer.Buffer
import scala.concurrent._, ExecutionContext.Implicits.global
import scala.async.Async.{ async, await }
import org.scalafmt.{ Scalafmt, Formatted }
import org.scalafmt.config.{ ScalafmtConfig, FilterMatcher }
import metaconfig.Configured.{ Ok, NotOk }

case class CheckContext(context: Context)(
  head_branch: String,
  head_sha: String,
) {
  val github = context.github
  val repo = context.repo()
  val log = context.log

  def listAllFiles(recursively: Boolean): Future[List[String]] = {
    // TODO: add pagination, the list can be very big
    github.gitdata.getTree(
      owner = repo.owner,
      repo = repo.repo,
      tree_sha = head_sha,
      recursive = if (recursively) 1 else js.undefined,
    ).map { response =>
      val paths = response.data.tree
        .asInstanceOf[js.Array[js.Dynamic]]
        .map { node => node.path.asInstanceOf[String] }
      // log.debug(paths)
      paths.toList
    }
  }

  def getContent(path: String): Future[String] = {
    github.repos.getContent(
      owner = repo.owner,
      repo = repo.repo,
      path = path,
      ref = head_sha,
    ).map { response =>
      log.info(js.JSON.stringify(response, space = 2))
      val content = Buffer.from(
        response.data.content.asInstanceOf[String],
        "base64"
      ).toString("utf-8")
      // log.debug(content)
      content
    }
  }

  private val checkName = "Scalafmt"

  def createCheck(): Future[String] =
    github.checks.create(
      name = checkName,
      owner = repo.owner,
      repo = repo.repo,
      status = "queued",
      head_branch = head_branch,
      head_sha = head_sha,
    ).map { _.data.id.toString }

  def updateCheck(checkId: String)(
    status: String,
    conclusion: js.UndefOr[String] = js.undefined,
    output: js.UndefOr[CheckOutput] = js.undefined,
    actions: js.UndefOr[js.Array[CheckAction]] = js.undefined,
  ): Future[Octokit.AnyResponse] =
    github.checks.update(
      name = checkName,
      check_run_id = checkId,
      owner = repo.owner,
      repo = repo.repo,
      status = status,
      conclusion = conclusion,
      completed_at = conclusion.map { _ => new js.Date().toISOString() },
      output = output,
      actions = actions,
    )

  def cancelCheck(checkId: String)(
    summary: String,
    details: js.UndefOr[String] = js.undefined,
  ) = updateCheck(checkId)(
      status = "completed",
      conclusion = "cancelled",
      output = new CheckOutput(
        title = "Formatting check was cancelled",
        summary = summary,
        text = details,
      )
    )

  def succeedCheck(checkId: String) =
    updateCheck(checkId)(
      status = "completed",
      conclusion = "success",
      output = new CheckOutput(
        title = "Formatting check succeeded",
        summary = "All files are well-formatted",
      )
    )

  def failCheck(checkId: String)(
    misformatted: List[String],
  ) = updateCheck(checkId)(
    status = "completed",
    conclusion = "failure",
    output = new CheckOutput(
      title = "Formatting check failed",
      summary = s"""|These files are mis-formatted:
                    |${misformatted.mkString("* `", "`\n* `", "`")}
                    |""".stripMargin,
    )
  )

  def checkFormatting(
    path: String,
    content: String,
    config: ScalafmtConfig,
  ): FormattingResult =
    Scalafmt.format(content, config) match {
      case Formatted.Failure(error) =>
        FormattingError(path, error.toString)
      case Formatted.Success(formatted) if content == formatted =>
        WellFormatted(path)
      case _ =>
        MisFormatted(path)
    }

  def run(
    checkId: String,
    config: ScalafmtConfig,
  ): Future[js.Any] = async {
    val matcher = FilterMatcher(
      config.project.includeFilters,
      config.project.excludeFilters
    )
    val paths = await(listAllFiles(recursively = true))
      .filter(matcher.matches)
    val futures = Future.traverse(paths) { path =>
      async {
        val content = getContent(path)
        val result = checkFormatting(path, await(content), config)
        log.info(result.toString)
        result
      }
    }
    val results = await(futures)
    val success = results.forall {
      case WellFormatted(_) => true
      case _ => false
    }
    await {
      if (success) succeedCheck(checkId)
      else failCheck(checkId)(
        results.collect { case MisFormatted(path) => path }
      )
    }
  }

  def readConfig(checkId: String): Future[Option[String]] = {
    val confPath = ".scalafmt.conf"
    getContent(confPath)
      .map { Some(_) }
      .recoverWith {
        case err: Throwable => async {
          log.warn(err.toString)
          val paths = await { listAllFiles(recursively = false) }
          if (paths.contains(confPath)) await {
            cancelCheck(checkId)(
              summary = s"Couldn't read the `.scalafmt.conf` config",
              details = Seq(
                "```",
                err.getMessage(),
                "```",
              ).mkString("\n"),
            ).map { _ => None }
          } else await {
            updateCheck(checkId)(
              status = "completed",
              conclusion = "neutral",
              output = new CheckOutput(
                title = "Formatting check needs a configuration",
                summary = "No Scalafmt configuration file is found. The formatting check won't run without it. You can add an empty config if you want to use Scalafmt defaults. Just commit a `.scalafmt.conf` file to this branch.",
              ),
              // TODO: add this when actions type is fixed: https://github.com/octokit/routes/issues/167#issuecomment-393601130
              // actions = js.Array(
              //   new CheckAction(
              //     label = "Create config",
              //     description = "Create an empty `.scalafmt.conf` file to use Scalafmt defaults",
              //     identifier = "create_config",
              //   ),
              // ),
            ).map { _ => None }
          }
        }
      }
  }

  def parseConfig(
    checkId: String,
    content: String
  ): Future[Option[ScalafmtConfig]] = {
    Scalafmt.parseHoconConfig(content) match {
      case Ok(config) => Future.successful(Some(config))
      case NotOk(err) =>
        log.warn(err.toString)
        cancelCheck(checkId)(
          summary = s"Couldn't parse the `.scalafmt.conf` config",
          details = s"```\n${err}\n```",
        ).map { _ => None }
    }
  }

  def run(): Future[js.Any] = async {
    val checkId = await { createCheck() }
    val configContentOpt = await { readConfig(checkId) }
    configContentOpt match {
      case None => ()
      case Some(content) => {
        val configOpt = await { parseConfig(checkId, content) }
        configOpt match {
          case None => ()
          case Some(config) => await { run(checkId, config) }
        }
      }
    }
  }

}

sealed trait FormattingResult
case class MisFormatted(path: String) extends FormattingResult
case class WellFormatted(path: String) extends FormattingResult
case class FormattingError(path: String, error: String) extends FormattingResult

class CheckOutput(
  val title: String,
  val summary: String,
  val text: js.UndefOr[String] = js.undefined,
  val annotations: js.UndefOr[js.Array[CheckAnnotation]] = js.undefined,
) extends js.Object

class CheckAnnotation(
  val filename: String,
  val blob_href: String,
  val start_line: Int,
  val end_line: Int,
  val warning_level: String,
  val message: String,
  val title: js.UndefOr[String] = js.undefined,
  val raw_details: js.UndefOr[String] = js.undefined,
) extends js.Object

class CheckAction(
  val label: String,
  val description: String,
  val identifier: String,
) extends js.Object
