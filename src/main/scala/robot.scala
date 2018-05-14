package laughedelic.scalafmt.probot

import scala.scalajs.js, js.annotation._
import laughedelic.probot._
import scala.concurrent._
import ExecutionContext.Implicits.global
import org.scalafmt.{ Scalafmt, Formatted }

object ScalafmtProbot {

  @JSExportTopLevel("probot")
  def probot(robot: Robot): Unit = {

    robot.on(js.Array("check_run", "check_suite"), { context =>
      // context.log.info(js.JSON.stringify(context.payload, space = 2))
      val payload = context.payload.asInstanceOf[js.Dynamic]
      val sha = payload.check_suite.after.toString

      val gh = new GHUtils(robot, context)
      gh.reportPreparing(sha)

      gh.listAllFiles(sha).map { paths =>
        Future.traverse(paths) { path =>
          gh.getContent(path, sha).map { content =>
            Scalafmt.format(
              code = content,
            ) match {
              case Formatted.Failure(error) => {
                context.log.error(s"failed to format ${path}: ${error}")
                gh.reportError(sha, path, error.getMessage)
              }
              case Formatted.Success(formatted) => {
                if (content != formatted) {
                  context.log.warn(s"mis-formatted: ${path}")
                  gh.reportFailure(sha, path)
                }
              }
            }
          }
        }.foreach { _ =>
          gh.reportSuccess(sha)
        }
      }

    })
  }
}
