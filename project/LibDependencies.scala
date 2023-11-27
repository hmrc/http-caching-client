import sbt._
object LibDependencies {

  private val cryptoVersion    = "7.6.0"
  private val httpVerbsVersion = "14.12.0"

  val common = Seq(
    "org.scalatest"         %% "scalatest"               % "3.2.17"  % Test,
    "com.vladsch.flexmark"  %  "flexmark-all"            % "0.64.8"  % Test,
    "org.mockito"           %% "mockito-scala-scalatest" % "1.17.14" % Test
  )

  val play28 = Seq(
    "uk.gov.hmrc" %% "crypto-json-play-28" % cryptoVersion,
    "uk.gov.hmrc" %% "http-verbs-play-28"  % httpVerbsVersion
  )

  val play29 = Seq(
    "uk.gov.hmrc" %% "crypto-json-play-29" % cryptoVersion,
    "uk.gov.hmrc" %% "http-verbs-play-29"  % httpVerbsVersion
  )

  val play30 = Seq(
    "uk.gov.hmrc" %% "crypto-json-play-30" % cryptoVersion,
    "uk.gov.hmrc" %% "http-verbs-play-30"  % httpVerbsVersion
  )
}
