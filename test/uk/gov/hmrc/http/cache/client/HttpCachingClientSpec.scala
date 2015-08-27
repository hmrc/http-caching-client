package uk.gov.hmrc.http.cache.client

import org.scalatest.mock.MockitoSugar
import play.api.libs.json._
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.SessionId
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

case class Field1(field1: String)

object Field1 {
  implicit val formats = Json.format[Field1]
}

object FormOnPage1 {
  implicit val formats = Json.format[FormOnPage1]
}

case class FormOnPage1(field1: String, field2: Boolean)
case class FormOnPage2(field1: Int)


class HttpCachingClientSpec extends UnitSpec with WithFakeApplication {

  implicit val hc = new HeaderCarrier(sessionId = Some(SessionId("ksc-session-id")))

  val source = "aSource"

  "The session cache client" should {

    val id = "httpSessionId"

    "fetch a map by id" in {

      val data = CacheMap(id, Map("form1" -> new JsObject(Seq("field1" -> JsString("value1")))))
      val client = SessionCachingForTest(data)

      val map = await(client.fetch())

      map should be (defined)

    }

    "return None if the map is not found" in {

      val client = SessionCachingForTest(new NotFoundException("not found"))
      await(client.fetch()) shouldBe None
    }

    "store an entry" in {
      val expectedResult = Map(id -> new JsObject(Seq("field1" -> JsString("value1"))))
      val data = Field1("value1")

      val client = SessionCachingForTest(id)
      val map = await(client.cache[Field1]("form1", data))

      map.data shouldBe expectedResult
    }

    "extract the sessionId from the HeaderCarrier" in {
      val data = CacheMap(id, Map.empty)
      val client = SessionCachingForTest(data)
      client.cacheId shouldBe "sessionId"
    }

    "delete an entry" in {
      val data = CacheMap(id, Map("form1" -> new JsObject(Seq("field1" -> JsString("value1")))))
      val client = SessionCachingForTest(data)

      val map = await(client.fetch())
      map should be (defined)

      client.remove()

      val deletedMap = await(client.fetch())
      deletedMap.get.data should be (empty)
    }
  }

  "A keystore map" should {

    val id = "httpSessionId"

    "read entries into case classes" in {
      val data = CacheMap(id, Map(
        "form1" -> new JsObject(Seq("field1" -> JsString("value1"))),
        "form2" -> new JsObject(Seq("field2" -> JsBoolean(true)))))

      implicit val formats = Json.format[Field1]

      val client = SessionCachingForTest(data)

      val entry = await(client.fetch()).get
      val f1o = entry.getEntry[Field1]("form1")
      f1o should not be empty
      f1o.get shouldBe Field1("value1")
    }

    "fetch and retrieve keyed data" in {

      val data = CacheMap(id, Map(
        "form1" -> new JsObject(Seq("field1" -> JsString("value1"))),
        "form2" -> new JsObject(Seq("field2" -> JsBoolean(true)))))

      implicit val formats = Json.format[Field1]

      val client = SessionCachingForTest(data)

      val f1o = await(client.fetchAndGetEntry("form1"))
      f1o should not be empty
      f1o.get shouldBe Field1("value1")
    }

    "return None if the entry is not found" in {
      val data = CacheMap(id, Map(
        "form1" -> new JsObject(Seq("field1" -> JsString("value1")))))

      implicit val formats = Json.format[Field1]

      val client = SessionCachingForTest(data)

      val entry = await(client.fetch()).get
      val f1o = entry.getEntry[Field1]("form2")
      f1o shouldBe empty
    }

    "return an exception for conversions errors" in {
      val data = CacheMap(id, Map(
        "form1" -> new JsObject(Seq("field1" -> JsBoolean(true)))))

      implicit val formats = Json.format[Field1]

      val client = SessionCachingForTest(data)

      val entry = await(client.fetch()).get

      intercept[KeyStoreEntryValidationException] {
        entry.getEntry[Field1]("form1")
      }
    }

  }


  "The short lived cache client" should {

    val id = "explicitlySetId"

    "fetch a map by id" in {

      val data = CacheMap(id, Map("form1" -> new JsObject(Seq("field1" -> JsString("value1")))))
      val client = ShortLivedCachingForTest(data)

      val map = await(client.fetch(id))

      map should be (defined)

    }

    "return None if the map is not found" in {

      val client = ShortLivedCachingForTest(new NotFoundException("not found"))
      await(client.fetch(id)) shouldBe None
    }

    "store an entry" in {
      val expectedResult = Map("form1" -> new JsObject(Seq("field1" -> JsString("value1"))))
      val data = Field1("value1")

      val client = ShortLivedCachingForTest(id, "form1")
      val map = await(client.cache[Field1](id, "form1", data))

      map.data shouldBe expectedResult
    }

  }

}

trait MockedSessionCache extends SessionCache with MockitoSugar {
  override val httpGet: HttpGet = mock[HttpGet]
  override val httpDelete: HttpDelete = mock[HttpDelete]
  override val httpPut: HttpPut = mock[HttpPut]
}

object SessionCachingForTest {

  private val source = "aSource"
  val aKey = "form1"

  def apply(map: CacheMap) = new MockedSessionCache {
    var cacheMap = map
    override private[client] def cacheId(implicit hc: HeaderCarrier): String = "sessionId"
    override lazy val baseUri = "https://on-left"
    override lazy val defaultSource: String = source
    override lazy val domain: String = "keystore"

    override def get(uri: String)(implicit hc: HeaderCarrier): Future[CacheMap] = Future.successful(cacheMap)
    override def put[T](uri: String, body: T)(implicit hc: HeaderCarrier, wts: Writes[T]): Future[CacheMap] = ???
    override def remove()(implicit hc: HeaderCarrier): Future[HttpResponse] = {
      cacheMap = CacheMap(map.id, Map.empty)
      Future.successful(HttpResponse(200))
    }
  }
  def apply(e: Exception) = new MockedSessionCache {
    override private[client] def cacheId(implicit hc: HeaderCarrier): String = "sessionId"
    override lazy val baseUri = "https://on-left"
    override lazy val defaultSource: String = source
    override lazy val domain: String = "keystore"

    override def get(uri: String)(implicit hc: HeaderCarrier): Future[CacheMap] = Future.failed(e)
  }
  def apply(key : String) = new MockedSessionCache {
    override private[client] def cacheId(implicit hc: HeaderCarrier): String = "sessionId"
    override lazy val baseUri = "https://on-left"
    override lazy val defaultSource: String = source
    override lazy val domain: String = "keystore"

    override def get(uri: String)(implicit hc: HeaderCarrier): Future[CacheMap] = ???
    override def put[T](uri: String, body: T)(implicit hc: HeaderCarrier, wts: Writes[T]): Future[CacheMap] =
      Future.successful(CacheMap(cacheId, Map(key -> wts.writes(body))))
  }
}

object ShortLivedCachingForTest {

  private val source = "aSource"

  def apply(map: CacheMap) = new ShortLivedHttpCaching {
    override lazy val defaultSource: String = source
    override lazy val baseUri = "https://on-right"
    override lazy val domain: String = "save4later"

    override def get(uri: String)(implicit hc: HeaderCarrier): Future[CacheMap] = Future.successful(map)
    override def put[T](uri: String, body: T)(implicit hc: HeaderCarrier, wts: Writes[T] ): Future[CacheMap] = ???
  }
  def apply(e: Exception) = new ShortLivedHttpCaching {
    override lazy val defaultSource: String = source
    override lazy val baseUri = "https://on-right"
    override lazy val domain: String = "save4later"

    override def get(uri: String)(implicit hc: HeaderCarrier): Future[CacheMap] = Future.failed(e)
  }
  def apply(id : String, key : String) = new ShortLivedHttpCaching {
    override lazy val defaultSource: String = source
    override lazy val baseUri = "https://on-right"
    override lazy val domain: String = "save4later"

    override def get(uri: String)(implicit hc: HeaderCarrier): Future[CacheMap] = ???
    override def put[T](uri: String, body: T)(implicit hc: HeaderCarrier, wts: Writes[T] ): Future[CacheMap] =
      Future.successful(CacheMap(id, Map(key -> wts.writes(body))))
  }
}