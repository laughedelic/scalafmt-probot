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
  "laughedelic" %%% "scalajs-probot" % "02ae5881+20180513-0153",
  "laughedelic" %%% "scalajs-octokit" % "3071db72+20180513-0513",
  "com.geirsson" %%% "scalafmt-core" % "1.5.1",
  "io.scalajs" %%% "nodejs" % "0.4.2",
)

scalaJSOutputWrapper := ("", """module.exports = exports.probot;""")

scalaJSLinkerConfig ~= { conf =>
  conf
    .withModuleKind(ModuleKind.CommonJSModule)
    .withOutputMode(OutputMode.ECMAScript2015)
}

artifactPath in (Compile, fullOptJS) := baseDirectory.value / "lib" / "index.js"
