package laughedelic.scalafmt.probot

import scala.scalajs.js
import laughedelic.probot._
import laughedelic.octokit.rest._
import io.scalajs.nodejs.buffer.Buffer
import scala.concurrent._, ExecutionContext.Implicits.global

case class GHUtils(
  robot: Robot,
  context: Context,
) {
  val github = context.github
  val payload = context.payload

  def listAllFiles(sha: String): Future[List[Path]] = {
    github.gitdata.getTree(
      owner = context.repo().owner,
      repo = context.repo().repo,
      tree_sha = "master",
      recursive = 1,
    ).map { response =>
      val files = response.data.tree
        .asInstanceOf[js.Array[js.Dynamic]]
        .map { node => node.path.asInstanceOf[Path] }
        .filter { path =>
          path.endsWith(".scala")
          // path.endsWith(".sbt")
        }
      context.log.debug(files)
      files.toList
    }
  }

  def getContent(path: String, sha: String): Future[String] = {
    github.repos.getContent(
      owner = context.repo().owner,
      repo = context.repo().repo,
      path = path,
      ref = sha,
    ).map { response =>
      val content = Buffer.from(
        response.data.content.asInstanceOf[String],
        "base64"
      ).toString("utf-8")
      // context.log.debug(content)
      content
    }
  }

  private val checkName = "Scalafmt"

  def createCheck(
    head_branch: String,
    head_sha: String,
  ): Future[String] =
    context.github.checks.create(
      name = checkName,
      owner = context.repo().owner,
      repo = context.repo().repo,
      status = "queued",
      head_branch = head_branch,
      head_sha = head_sha,
    ).map { _.data.id.toString }

  def updateCheck(checkId: String)(
    status: String,
    conclusion: js.UndefOr[String] = js.undefined,
    output: js.UndefOr[CheckOutput] = js.undefined,
  ): Future[Octokit.AnyResponse] =
    context.github.checks.update(
      name = checkName,
      check_run_id = checkId,
      owner = context.repo().owner,
      repo = context.repo().repo,
      status = status,
      conclusion = conclusion,
      completed_at = conclusion.map { _ => new js.Date().toISOString() },
      output = output,
    )
}

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
