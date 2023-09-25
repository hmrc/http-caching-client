val scala2_12 = "2.12.18"
val scala2_13 = "2.13.12"

ThisBuild / majorVersion     := 11
ThisBuild / isPublicArtefact := true
ThisBuild / scalaVersion     := scala2_13

lazy val library = (project in file("."))
  .settings(publish / skip := true)
  .aggregate(
    playPartialsPlay28,
    playPartialsPlay29
  )

val sharedSources = Seq(
  Compile / unmanagedSourceDirectories   += baseDirectory.value / s"../src-common/main/scala",
  Compile / unmanagedResourceDirectories += baseDirectory.value / s"../src-common/main/resources",
  Test    / unmanagedSourceDirectories   += baseDirectory.value / s"../src-common/test/scala",
  Test    / unmanagedResourceDirectories += baseDirectory.value / s"../src-common/test/resources"
)

lazy val playPartialsPlay28 = Project("http-caching-client-play-28", file("http-caching-client-play-28"))
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
  .settings(
    crossScalaVersions := Seq(scala2_12, scala2_13),
    sharedSources,
    libraryDependencies ++= LibDependencies.common ++ LibDependencies.play28
  )

lazy val playPartialsPlay29 = Project("http-caching-client-play-29", file("http-caching-client-play-29"))
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
  .settings(
    crossScalaVersions := Seq(scala2_13),
    sharedSources,
    libraryDependencies ++= LibDependencies.common ++ LibDependencies.play29
  )

lazy val playPartialsPlay30 = Project("http-caching-client-play-30", file("http-caching-client-play-30"))
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
  .settings(
    crossScalaVersions := Seq(scala2_13),
    sharedSources,
    libraryDependencies ++= LibDependencies.common ++ LibDependencies.play30
  )
