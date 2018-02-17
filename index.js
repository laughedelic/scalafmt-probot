const scalafmt = require('scalafmt')

module.exports = (robot) => {
  const content = async (context, file) => {
    const { github, payload } = context
    const response = await github.repos.getContent(
      context.repo({
        path: file,
        ref: payload.head_commit.id
      })
    )
    const content = Buffer.from(response.data.content, 'base64').toString('utf-8')
    // robot.log.warn(content)
    return content
  }

  const setStatus = async (context, state, descr) => {
    const { github, payload } = context
    robot.log(`${state}: ${descr}`)

    github.repos.createStatus(
      context.repo({
        sha: payload.head_commit.id,
        state: state,
        // target_url: context.payload.head_commit.url,
        description: descr,
        context: 'scalafmt'
      })
    )
  }

  const reportSuccess = async (context) => {
    await setStatus(context, 'success', 'All files are formatted')
  }

  const reportFailure = async (context, path) => {
    await setStatus(context, 'failure', `${path} is mis-formatted`)
  }

  const reportProgress = async (context, count = undefined, total = undefined) => {
    if (count && total) {
      const percentage = Math.round(count * 100 / total)
      await setStatus(context, 'pending', `Checked ${percentage}%`)
    } else {
      await setStatus(context, 'pending', 'Preparing')
    }
  }

  const reportError = async (context, error) => {
    await setStatus(context, 'pending', `An error occured: ${error}`)
  }

  robot.on('push', async context => {
    const { github, payload } = context
    reportProgress(context)

    // robot.log(getFiles(context))

    // const files = commit.modified.concat(commit.added)
    const response = await github.gitdata.getTree(
      context.repo({
        sha: payload.head_commit.id,
        recursive: true
      })
    )
    // robot.log.debug(response.data)
    const files = response.data.tree
      .map(node => node.path)
      .filter(path => path.endsWith('.scala') || path.endsWith('.sbt'))
    robot.log.debug(files)

    const config = await content(context, '.scalafmt.conf')

    let count = 0
    const total = files.length
    for (const file of files) {
      try {
        if (count > 0) {
          reportProgress(context, count, total)
        }
        const before = await content(context, file)
        const after = scalafmt.format(before, file.endsWith('.sbt'), config)
        if (before !== after) {
          robot.log.error(`mis-formatted: ${file}`)
          await reportFailure(context, file)
          return
        }
        count += 1
      } catch (error) {
        robot.log.error(`${file}: ${error}`)
        await reportError(context, error)
        return
      }
    }
    // process.nextTick(() => reportSuccess(context))
    await reportSuccess(context)
  })
}
