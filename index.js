const scalafmt = require('scalafmt')
const GHUtils = require('./lib/gh.js')

module.exports = (robot) => {
  robot.on('push', async context => {
    const gh = new GHUtils(robot, context)

    gh.reportProgress()

    const config = await gh.getContent('.scalafmt.conf')
    const files = await gh.listAllFiles()

    let count = 0
    const total = files.length
    for (const file of files) {
      try {
        if (count > 0) {
          gh.reportProgress(count, total)
        }
        const before = await gh.getContent(file)
        const after = scalafmt.format(before, file.endsWith('.sbt'), config)
        if (before !== after) {
          robot.log.error(`mis-formatted: ${file}`)
          await gh.reportFailure(file)
          return
        }
        count += 1
      } catch (error) {
        robot.log.error(`${file}: ${error}`)
        await gh.reportError(error)
        return
      }
    }

    // process.nextTick(() => gh.reportSuccess())
    await gh.reportSuccess()
  })
}
