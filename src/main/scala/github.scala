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

  def setStatus(
    sha: String,
    state: StatusState,
    descr: String,
  ): Future[Octokit.AnyResponse] = {
    context.log(s"${state}: ${descr}")

    github.repos.createStatus(
      owner = context.repo().owner,
      repo = context.repo().repo,
      sha = sha,
      state = state.toString,
      description = descr,
      context = "scalafmt",
      // target_url = payload.head_commit.url,
    )
  }

  def reportPreparing(sha: String): Future[Octokit.AnyResponse] =
    setStatus(sha, StatusState.pending, "Preparing")

  def reportSuccess(sha: String): Future[Octokit.AnyResponse] =
    setStatus(sha, StatusState.success, "All files are well-formatted")

  def reportFailure(sha: String, path: Path): Future[Octokit.AnyResponse] =
    setStatus(sha, StatusState.failure, s"File ${path} is mis-formatted")

  def reportError(sha: String, path: Path, error: String): Future[Octokit.AnyResponse] =
    setStatus(sha, StatusState.error, s"Error occured in ${path}: ${error}")

  def reportProgress(
    sha: String,
    count: Int,
    total: Int,
  ): Future[Octokit.AnyResponse] = {
    val percentage = Math.round(count * 100 / total)
    setStatus(sha, StatusState.pending, s"Checking ${total} files: ${percentage}% done")
  }

}

sealed trait StatusState
object StatusState {
  case object error extends StatusState
  case object failure extends StatusState
  case object pending extends StatusState
  case object success extends StatusState
}
