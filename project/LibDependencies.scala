import sbt._
object LibDependencies {

  private val cryptoVersion    = "8.0.0"
  private val httpVerbsVersion = "15.0.0"

  val common = Seq(
    "org.scalatest"         %% "scalatest"               % "3.2.18"   % Test,
    "com.vladsch.flexmark"  %  "flexmark-all"            % "0.64.8"   % Test,
    "org.scalatestplus"     %% "mockito-3-4"             % "3.2.10.0" % Test
  )

  val play28 = Seq(
    "uk.gov.hmrc" %% "crypto-json-play-28"     % cryptoVersion,
    "uk.gov.hmrc" %% "http-verbs-play-28"      % httpVerbsVersion,
    "uk.gov.hmrc" %% "http-verbs-test-play-28" % httpVerbsVersion  % Test
  )

  val play29 = Seq(
    "uk.gov.hmrc" %% "crypto-json-play-29"     % cryptoVersion,
    "uk.gov.hmrc" %% "http-verbs-play-29"      % httpVerbsVersion,
    "uk.gov.hmrc" %% "http-verbs-test-play-29" % httpVerbsVersion  % Test
  )

  val play30 = Seq(
    "uk.gov.hmrc" %% "crypto-json-play-30"     % cryptoVersion,
    "uk.gov.hmrc" %% "http-verbs-play-30"      % httpVerbsVersion,
    "uk.gov.hmrc" %% "http-verbs-test-play-30" % httpVerbsVersion  % Test
  )
}
