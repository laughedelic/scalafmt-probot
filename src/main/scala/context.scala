package laughedelic.scalafmt.probot

import scala.scalajs.js
import laughedelic.probot._
import laughedelic.octokit.rest._
import io.scalajs.nodejs.buffer.Buffer
import scala.concurrent._, ExecutionContext.Implicits.global
import scala.async.Async.{ async, await }
import org.scalafmt.{ Scalafmt, Formatted }
import org.scalafmt.config.ScalafmtConfig
import metaconfig.Configured.{ Ok, NotOk }

case class CheckContext(context: Context)(
  head_branch: String,
  head_sha: String,
) {
  val github = context.github
  val repo = context.repo()
  val log = context.log

  def listAllFiles(): Future[List[String]] = {
    github.gitdata.getTree(
      owner = repo.owner,
      repo = repo.repo,
      tree_sha = head_sha,
      recursive = 1,
    ).map { response =>
      val files = response.data.tree
        .asInstanceOf[js.Array[js.Dynamic]]
        .map { node => node.path.asInstanceOf[String] }
        .filter { path =>
          path.endsWith(".scala")
          // path.endsWith(".sbt")
        }
      // log.debug(files)
      files.toList
    }
  }

  def getContent(path: String): Future[String] = {
    github.repos.getContent(
      owner = repo.owner,
      repo = repo.repo,
      path = path,
      ref = head_sha,
    ).map { response =>
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
    val paths = listAllFiles()
    val futures = Future.traverse(await(paths)) { path =>
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

  def run(): Future[js.Any] = async {
    val checkId = await { createCheck() }
    // TODO: report neutral status when there is no config, or use default one
    val configContent = await { getContent(".scalafmt.conf") }
    Scalafmt.parseHoconConfig(configContent) match {
      case NotOk(err) => await {
        cancelCheck(checkId)(
          summary = s"Couldn't parse the `.scalafmt.conf` config",
          details = s"```\n${err}\n```",
        )
      }
      case Ok(config) => await {
        run(checkId, config)
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
