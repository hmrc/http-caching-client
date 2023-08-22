import sbt.Keys._
import sbt._

val compileDependencies = PlayCrossCompilation.dependencies(
  play28 = Seq(
    "uk.gov.hmrc" %% "crypto-json-play-28" % "7.3.0",
    "uk.gov.hmrc" %% "http-verbs-play-28"  % "14.10.0"
  )
)

val testDependencies = PlayCrossCompilation.dependencies(
  shared = Seq(
    "org.scalatest"         %% "scalatest"     % "3.1.2"   % Test,
    "org.mockito"           %% "mockito-scala" % "1.5.11"  % Test,
    "com.vladsch.flexmark"  %  "flexmark-all"  % "0.35.10" % Test
  )
)

val scala2_12 = "2.12.17"
val scala2_13 = "2.13.9"

lazy val library = Project("http-caching-client", file("."))
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
  .settings(
    majorVersion := 10,
    isPublicArtefact := true,
    scalaVersion := scala2_12,
    crossScalaVersions := Seq(scala2_12, scala2_13),
    libraryDependencies ++= compileDependencies ++ testDependencies
  )
  .settings(PlayCrossCompilation.playCrossCompilationSettings)
