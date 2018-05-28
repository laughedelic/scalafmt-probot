import org.scalajs.core.tools.linker.standard._

name := "scalafmt-probot"
organization := "laughedelic"
description := ""

homepage := Some(url(s"https://github.com/${organization.value}/${name.value}"))
scmInfo in ThisBuild := Some(ScmInfo(
  homepage.value.get,
  s"scm:git:git@github.com:${organization.value}/${name.value}.git"
))

licenses := Seq("MPL-2.0" -> url("https://www.mozilla.org/en-US/MPL/2.0"))
developers := List(Developer(
  "laughedelic",
  "Alexey Alekhin",
  "laughedelic@gmail.com",
  url("https://github.com/laughedelic")
))

scalaVersion := "2.12.6"
scalacOptions ++= Seq(
  "-Yrangepos",
  "-P:scalajs:sjsDefinedByDefault",
  "-language:implicitConversions",
  "-deprecation",
  "-feature",
  "-Xlint"
)

enablePlugins(ScalaJSPlugin)

resolvers += Resolver.bintrayRepo("laughedelic", "maven")
libraryDependencies ++= Seq(
  "laughedelic" %%% "scalajs-probot" % "b7ff2bca",
  "laughedelic" %%% "scalajs-octokit" % "aa984a0d",
  "com.geirsson" %%% "scalafmt-core" % "1.6.0-RC1",
  "io.scalajs" %%% "nodejs" % "0.4.2",
)

scalaJSOutputWrapper := ("", """module.exports = exports.probot;""")

scalaJSLinkerConfig ~= { conf =>
  conf
    .withModuleKind(ModuleKind.CommonJSModule)
    .withOutputMode(OutputMode.ECMAScript2015)
}

artifactPath in (Compile, fastOptJS) := baseDirectory.value / "lib" / "index.js"
artifactPath in (Compile, fullOptJS) := baseDirectory.value / "lib" / "index.js"
