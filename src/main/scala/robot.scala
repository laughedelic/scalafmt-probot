package laughedelic.scalafmt.probot

import scala.scalajs.js, js.annotation._
import laughedelic.probot._
import scala.concurrent._, ExecutionContext.Implicits.global
import scala.async.Async.{ async, await }
import org.scalafmt.{ Scalafmt, Formatted }
import org.scalafmt.config.ScalafmtConfig
import metaconfig.Configured.{ Ok, NotOk }

object ScalafmtProbot {

  def checkFormatting(path: String, content: String, config: ScalafmtConfig): FormattingResult =
    Scalafmt.format(content, config) match {
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
      context.log.info(js.JSON.stringify(context.payload, space = 2))
      val payload = context.payload.asDynamic
      val sha = payload.check_suite.head_sha.toString

      val gh = new GHUtils(robot, context)

      val checkId = await {
        gh.createCheck(
          head_branch = payload.check_suite.head_branch.toString,
          head_sha = sha,
        )
      }

      // TODO: report neutral status when there is no config, or use default one
      val configContent = gh.getContent(".scalafmt.conf", sha)

      Scalafmt.parseHoconConfig(await(configContent)) match {
        case NotOk(err) => await {
          gh.updateCheck(checkId)(
            status = "completed",
            conclusion = "cancelled",
            output = new CheckOutput(
              title = "Formatting check was cancelled",
              summary = s"Couldn't parse the `.scalafmt.conf` config",
              text = s"```\n${err}\n```",
            )
          )
        }
        case Ok(config) => {
          val paths = gh.listAllFiles(sha)
          val futures = Future.traverse(await(paths)) { path =>
            async {
              val content = gh.getContent(path, sha)
              checkFormatting(path, await(content), config)
            }
          }

          val results = await(futures)
          results.foreach{ result =>
            context.log.info(result.toString)
          }

          val success = results.forall {
            case WellFormatted(_) => true
            case _ => false
          }
          val misformatted =
            results.collect { case MisFormatted(path) => path }

          await {
            if (success) gh.updateCheck(checkId)(
              status = "completed",
              conclusion = "success",
              output = new CheckOutput(
                title = "Formatting check succeeded",
                summary = "All files are well-formatted",
              )
            )
            else gh.updateCheck(checkId)(
              status = "completed",
              conclusion = "failure",
              output = new CheckOutput(
                title = "Formatting check failed",
                summary = s"""|These files are mis-formatted:
                              |${misformatted.mkString("* `", "`\n* `", "`")}
                              |""".stripMargin,
              )
            )
          }
        }
      }
    }}
  }
}

sealed trait FormattingResult
case class MisFormatted(path: String) extends FormattingResult
case class WellFormatted(path: String) extends FormattingResult
case class FormattingError(path: String, error: String) extends FormattingResult
