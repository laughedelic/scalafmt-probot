package laughedelic.scalafmt.probot

import scala.scalajs.js, js.annotation._
import laughedelic.probot._
import scala.concurrent._, ExecutionContext.Implicits.global
import scala.async.Async.{ async, await }
import org.scalafmt.{ Scalafmt, Formatted }

object ScalafmtProbot {

  def checkFormatting(path: String, content: String): FormattingResult =
    Scalafmt.format(code = content) match {
      case Formatted.Failure(error) =>
        FormattingError(path, error.toString)
      case Formatted.Success(formatted) if content == formatted =>
        WellFormatted(path)
      case _ =>
        MisFormatted(path)
    }

  @JSExportTopLevel("probot")
  def probot(robot: Robot): Unit = {

    robot.on(
      "check_suite.requested",
      "check_suite.rerequested",
      // "check_run.rerequested", // the payload shape is different
    ) { context => async {
    // robot.on("pull_request.synchronize", { context =>
      context.log.info(js.JSON.stringify(context.payload, space = 2))
      val payload = context.payload.asDynamic
      val sha = payload.check_suite.head_sha.toString

      val gh = new GHUtils(robot, context)

      val runID = await {
        context.github.checks.create(
          name = "Formatting check",
          owner = context.repo().owner,
          repo = context.repo().repo,
          head_branch = payload.check_suite.head_branch.toString,
          head_sha = sha,
          status = "in_progress",
        ).map { _.data.id.toString }
      }

      val paths = await { gh.listAllFiles(sha) }

      val results: List[FormattingResult] = await {
        Future.traverse(paths) { path =>
          async {
            val content = await { gh.getContent(path, sha) }
            checkFormatting(path, content)
          }
        }
      }

      results.foreach{ result => context.log.info(result.toString) }

      val success = results.forall {
        case WellFormatted(_) => true
        case _ => false
      }
      val conclusion = if (success) "success" else "failure"
      val misformatted = results.collect { case MisFormatted(path) => path }

      await {
        context.github.checks.update(
          check_run_id = runID,
          name = "Formatting check",
          owner = context.repo().owner,
          repo = context.repo().repo,
          status = "completed",
          conclusion = conclusion,
          completed_at = new js.Date().toISOString(),
          output = js.Dynamic.literal(
            title = "Scalafmt check",
            summary =
              if (success) "All files are well-formatted"
              else s"""These files are mis-formatted:\n${misformatted.mkString("* `", "`\n* `", "`")}""",
          )
        )
      }
    }}
  }
}

sealed trait FormattingResult
case class MisFormatted(path: String) extends FormattingResult
case class WellFormatted(path: String) extends FormattingResult
case class FormattingError(path: String, error: String) extends FormattingResult
