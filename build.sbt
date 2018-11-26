import sbt.Keys._
import sbt._
import uk.gov.hmrc.versioning.SbtGitVersioning

val appName = "http-caching-client"

val compileDependencies = Seq(
  "com.typesafe.play" %% "play-json"       % "2.5.15" % "provided",
  "uk.gov.hmrc"       %% "json-encryption" % "4.0.0",
  "uk.gov.hmrc"       %% "http-core"       % "0.5.0"
)

val testDependencies = Seq(
  "org.scalatest" %% "scalatest"   % "3.0.1" % "test",
  "org.pegdown"   %  "pegdown"     % "1.6.0" % "test",
  "org.mockito"   %  "mockito-all" % "1.9.5" % "test"
)

lazy val library = Project(appName, file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory)
  .settings(
    majorVersion := 8,
    makePublicallyAvailableOnBintray := true,
    libraryDependencies ++= compileDependencies ++ testDependencies,
    resolvers := Seq(
      Resolver.bintrayRepo("hmrc", "releases"),
      Resolver.typesafeRepo("releases")
    )
  )
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
