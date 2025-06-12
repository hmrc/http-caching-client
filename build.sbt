val scala2_13 = "2.13.16"
val scala3    = "3.3.6"

ThisBuild / majorVersion     := 12
ThisBuild / isPublicArtefact := true
ThisBuild / scalaVersion     := scala2_13

// Disable multiple project tests running at the same time
// https://www.scala-sbt.org/1.x/docs/Parallel-Execution.html
Global / concurrentRestrictions += Tags.limitSum(1, Tags.Test, Tags.Untagged)

lazy val library = (project in file("."))
  .settings(publish / skip := true)
  .aggregate(playPartialsPlay30)

val sharedSources = Seq(
  Compile / unmanagedSourceDirectories   += baseDirectory.value / s"../src-common/main/scala",
  Compile / unmanagedResourceDirectories += baseDirectory.value / s"../src-common/main/resources",
  Test    / unmanagedSourceDirectories   += baseDirectory.value / s"../src-common/test/scala",
  Test    / unmanagedResourceDirectories += baseDirectory.value / s"../src-common/test/resources"
)

lazy val playPartialsPlay30 = Project("http-caching-client-play-30", file("http-caching-client-play-30"))
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
  .settings(
    crossScalaVersions := Seq(scala2_13, scala3),
    sharedSources,
    libraryDependencies ++= LibDependencies.common ++ LibDependencies.play30
  )
