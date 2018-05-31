package laughedelic.scalafmt.probot

import scala.scalajs.js
import laughedelic.probot._
import laughedelic.octokit.rest._
import io.scalajs.nodejs.buffer.Buffer
import scala.concurrent._, ExecutionContext.Implicits.global

case class MinimalPayload(
  head_branch: String,
  head_sha: String,
)

case class GHUtils(
  github: GitHubAPI,
  repo: ContextRepo,
  payload: MinimalPayload,
  log: Logger,
) {

  def listAllFiles(): Future[List[Path]] = {
    github.gitdata.getTree(
      owner = repo.owner,
      repo = repo.repo,
      tree_sha = payload.head_sha,
      recursive = 1,
    ).map { response =>
      val files = response.data.tree
        .asInstanceOf[js.Array[js.Dynamic]]
        .map { node => node.path.asInstanceOf[Path] }
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
      ref = payload.head_sha,
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
      head_branch = payload.head_branch,
      head_sha = payload.head_sha,
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
}

object GHUtils {

  def apply(context: Context)(payload: MinimalPayload): GHUtils =
    GHUtils(context.github, context.repo(), payload, context.log)
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
