import sbt._
object LibDependencies {

  private val cryptoVersion    = "8.2.0"
  private val httpVerbsVersion = "15.2.0"

  val common = Seq(
    "org.scalatest"         %% "scalatest"               % "3.2.18"   % Test,
    "com.vladsch.flexmark"  %  "flexmark-all"            % "0.64.8"   % Test,
    "org.scalatestplus"     %% "mockito-3-4"             % "3.2.10.0" % Test
  )

  val play30 = Seq(
    "uk.gov.hmrc" %% "crypto-json-play-30"     % cryptoVersion,
    "uk.gov.hmrc" %% "http-verbs-play-30"      % httpVerbsVersion,
    "uk.gov.hmrc" %% "http-verbs-test-play-30" % httpVerbsVersion  % Test
  )
}
