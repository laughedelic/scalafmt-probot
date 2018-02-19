class GHUtils {

  constructor(robot, context) {
    this.robot = robot
    this.context = context
    this.github = context.github
    this.payload = context.payload
  }

  async listAllFiles() {
    // const files = commit.modified.concat(commit.added)
    const response = await this.github.gitdata.getTree(
      this.context.repo({
        sha: this.payload.head_commit.id,
        recursive: true
      })
    )
    // this.robot.log.debug(response.data)

    const files = response.data.tree
      .map(node => node.path)
      .filter(path => path.endsWith('.scala') || path.endsWith('.sbt'))

    this.robot.log.debug(files)
    return files
  }

  async getContent(file) {
    const response = await this.github.repos.getContent(
      this.context.repo({
        path: file,
        ref: this.payload.head_commit.id
      })
    )
    const content = Buffer.from(response.data.content, 'base64').toString('utf-8')
    // this.robot.log.warn(content)
    return content
  }

  async setStatus(state, descr) {
    this.robot.log(`${state}: ${descr}`)

    this.github.repos.createStatus(
      this.context.repo({
        sha: this.payload.head_commit.id,
        state: state,
        // target_url: this.context.payload.head_commit.url,
        description: descr,
        context: 'scalafmt'
      })
    )
  }

  async reportSuccess() {
    await this.setStatus('success', 'All files are well-formatted')
  }

  async reportFailure(path) {
    await this.setStatus('failure', `File ${path} is mis-formatted`)
  }

  async reportProgress(count = undefined, total = undefined) {
    if (count && total) {
      const percentage = Math.round(count * 100 / total)
      await this.setStatus('pending', `Checking ${total} files: ${percentage}% done`)
    } else {
      await this.setStatus('pending', 'Preparing')
    }
  }

  async reportError(error) {
    await this.setStatus('pending', `An error occured: ${error}`)
  }
}

module.exports = GHUtils
