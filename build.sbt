import sbt.Keys._
import sbt._
import uk.gov.hmrc.versioning.SbtGitVersioning

val appName = "http-caching-client"

val compileDependencies = PlayCrossCompilation.dependencies(
  play25 = Seq(
    "uk.gov.hmrc"       %% "json-encryption"  % "4.7.0-play-25",
    "com.typesafe.play" %% "play-json"        % "2.5.19",
    "uk.gov.hmrc"       %% "http-verbs"       % "10.7.0-play-25"
  ),
  play26 = Seq(
    "uk.gov.hmrc"       %% "json-encryption"  % "4.7.0-play-26",
    "com.typesafe.play" %% "play-json"        % "2.6.14",
    "uk.gov.hmrc"       %% "http-verbs"       % "10.7.0-play-26"
  ),
  play27 = Seq(
    "uk.gov.hmrc"       %% "json-encryption"  % "4.7.0-play-27",
    "com.typesafe.play" %% "play-json"        % "2.7.4",
    "uk.gov.hmrc"       %% "http-verbs"       % "10.7.0-play-27"
  )
)

val testDependencies = PlayCrossCompilation.dependencies(
  shared = Seq(
    "org.scalatest"        %% "scalatest"    % "3.0.5"   % "test",
    "com.vladsch.flexmark" %  "flexmark-all" % "0.35.10" % "test",
    "org.mockito"          %  "mockito-all"  % "1.9.5"   % "test"
  )
)

lazy val library = Project(appName, file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory)
  .settings(
    majorVersion := 9,
    makePublicallyAvailableOnBintray := true,
    libraryDependencies ++= compileDependencies ++ testDependencies,
    scalaVersion := "2.11.12",
    crossScalaVersions := Seq("2.11.12", "2.12.8"),
    resolvers := Seq(
      Resolver.bintrayRepo("hmrc", "releases"),
      Resolver.typesafeRepo("releases")
    )
  )
  .settings(PlayCrossCompilation.playCrossCompilationSettings)
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
