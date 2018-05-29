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

}
