package laughedelic.scalafmt.probot

import laughedelic.probot._
import scala.concurrent._, ExecutionContext.Implicits.global

object ScalafmtProbot {

  def plugin(app: Application): Unit = {

    app.on(
      "check_suite.requested",
      "check_suite.rerequested",
    ) { context =>
      val check_suite = context.payload.asDynamic.check_suite
      CheckContext(context)(
        head_branch = check_suite.head_branch.toString,
        head_sha    = check_suite.head_sha.toString,
      ).run()
    }

    app.on(
      "check_run.rerequested",
    ) { context =>
      val check_suite = context.payload.asDynamic.check_run.check_suite
      CheckContext(context)(
        head_branch = check_suite.head_branch.toString,
        head_sha    = check_suite.head_sha.toString,
      ).run()
    }
  }

  def main(args: Array[String]): Unit = Probot.run(plugin)

}
