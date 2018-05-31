package laughedelic.scalafmt.probot

import scala.scalajs.js, js.annotation._
import laughedelic.probot._
import scala.concurrent._, ExecutionContext.Implicits.global

object ScalafmtProbot {

  @JSExportTopLevel("probot")
  def probot(robot: Robot): Unit = {

    robot.on(
      "check_suite.requested",
      "check_suite.rerequested",
    ) { context =>
      val check_suite = context.payload.asDynamic.check_suite
      CheckContext(context)(
        head_branch = check_suite.head_branch.toString,
        head_sha    = check_suite.head_sha.toString,
      ).run()
    }

    robot.on(
      "check_run.rerequested",
    ) { context =>
      val check_suite = context.payload.asDynamic.check_run.check_suite
      CheckContext(context)(
        head_branch = check_suite.head_branch.toString,
        head_sha    = check_suite.head_sha.toString,
      ).run()
    }
  }
}
