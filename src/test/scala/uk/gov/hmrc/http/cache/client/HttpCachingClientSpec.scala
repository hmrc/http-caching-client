/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.http.cache.client

import org.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.SessionId

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

case class Field1(field1: String)

object Field1 {
  implicit val formats = Json.format[Field1]
}

object FormOnPage1 {
  implicit val formats = Json.format[FormOnPage1]
}

case class FormOnPage1(field1: String, field2: Boolean)
case class FormOnPage2(field1: Int)

class HttpCachingClientSpec extends AnyWordSpecLike with Matchers with ScalaFutures {

  implicit val hc              = new HeaderCarrier(sessionId = Some(SessionId("ksc-session-id")))
  implicit val defaultPatience = PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  val source = "aSource"

  "The session cache client" should {

    val id = "httpSessionId"

    "fetch a map by id" in {

      val data   = CacheMap(id, Map("form1" -> new JsObject(Map("field1" -> JsString("value1")))))
      val client = SessionCachingForTest(data)

      val map = client.fetch().futureValue

      map should be(defined)

    }

    "return None if the map is not found" in {

      val client = SessionCachingForTest(new NotFoundException("not found"))
      client.fetch().futureValue shouldBe None
    }

    "store an entry" in {
      val expectedResult = Map(id -> new JsObject(Map("field1" -> JsString("value1"))))
      val data           = Field1("value1")

      val client = SessionCachingForTest(id)
      val map    = client.cache[Field1]("form1", data).futureValue

      map.data shouldBe expectedResult
    }

    "extract the sessionId from the HeaderCarrier" in {
      val data   = CacheMap(id, Map.empty)
      val client = SessionCachingForTest(data)
      client.cacheId.futureValue shouldBe "sessionId"
    }

    "delete an entry" in {
      val data   = CacheMap(id, Map("form1" -> new JsObject(Map("field1" -> JsString("value1")))))
      val client = SessionCachingForTest(data)

      val map = client.fetch().futureValue
      map should be(defined)

      client.remove()

      val deletedMap = client.fetch().futureValue
      deletedMap.get.data should be(empty)
    }
  }

  "A keystore map" should {

    val id = "httpSessionId"

    "read entries into case classes" in {
      val data = CacheMap(
        id,
        Map(
          "form1" -> new JsObject(Map("field1" -> JsString("value1"))),
          "form2" -> new JsObject(Map("field2" -> JsBoolean(true)))))

      implicit val formats = Json.format[Field1]

      val client = SessionCachingForTest(data)

      val entry = client.fetch().futureValue.get
      val f1o   = entry.getEntry[Field1]("form1")
      f1o     should not be empty
      f1o.get shouldBe Field1("value1")
    }

    "fetch and retrieve keyed data" in {

      val data = CacheMap(
        id,
        Map(
          "form1" -> new JsObject(Map("field1" -> JsString("value1"))),
          "form2" -> new JsObject(Map("field2" -> JsBoolean(true)))))

      implicit val formats = Json.format[Field1]

      val client = SessionCachingForTest(data)

      val f1o = client.fetchAndGetEntry("form1").futureValue
      f1o     should not be empty
      f1o.get shouldBe Field1("value1")
    }

    "return None if the entry is not found" in {
      val data = CacheMap(id, Map("form1" -> new JsObject(Map("field1" -> JsString("value1")))))

      implicit val formats = Json.format[Field1]

      val client = SessionCachingForTest(data)

      val entry = client.fetch().futureValue.get
      val f1o   = entry.getEntry[Field1]("form2")
      f1o shouldBe empty
    }

    "return an exception for conversions errors" in {
      val data = CacheMap(id, Map("form1" -> new JsObject(Map("field1" -> JsBoolean(true)))))

      implicit val formats = Json.format[Field1]

      val client = SessionCachingForTest(data)

      val entry = client.fetch().futureValue.get

      intercept[KeyStoreEntryValidationException] {
        entry.getEntry[Field1]("form1")
      }
    }

  }

  "The short lived cache client" should {

    val id = "explicitlySetId"

    "fetch a map by id" in {

      val data   = CacheMap(id, Map("form1" -> new JsObject(Map("field1" -> JsString("value1")))))
      val client = ShortLivedCachingForTest(data)

      val map = client.fetch(id).futureValue

      map should be(defined)

    }

    "return None if the map is not found" in {

      val client = ShortLivedCachingForTest(new NotFoundException("not found"))
      client.fetch(id).futureValue shouldBe None
    }

    "store an entry" in {
      val expectedResult = Map("form1" -> new JsObject(Map("field1" -> JsString("value1"))))
      val data           = Field1("value1")

      val client = ShortLivedCachingForTest(id, "form1")
      val map    = client.cache[Field1](id, "form1", data).futureValue

      map.data shouldBe expectedResult
    }

  }

}

trait MockedSessionCache extends SessionCache with MockitoSugar {
  override val http = mock[CoreGet with CorePut with CoreDelete]
}

object SessionCachingForTest {

  private val source = "aSource"
  val aKey           = "form1"

  def apply(map: CacheMap) = new MockedSessionCache {
    var cacheMap                                                     = map
    override private[client] def cacheId(implicit hc: HeaderCarrier) = Future.successful("sessionId")
    override lazy val baseUri                                        = "https://on-left"
    override lazy val defaultSource: String                          = source
    override lazy val domain: String                                 = "keystore"

    override def get(uri: String)(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[CacheMap] =
      Future.successful(cacheMap)
    override def put[T](
      uri: String,
      body: T)(implicit hc: HeaderCarrier, wts: Writes[T], executionContext: ExecutionContext): Future[CacheMap] = ???
    override def remove()(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[HttpResponse] = {
      cacheMap = CacheMap(map.id, Map.empty)
      Future.successful(HttpResponse(200))
    }
  }
  def apply(e: Exception) = new MockedSessionCache {
    override private[client] def cacheId(implicit hc: HeaderCarrier) = Future.successful("sessionId")
    override lazy val baseUri                                        = "https://on-left"
    override lazy val defaultSource: String                          = source
    override lazy val domain: String                                 = "keystore"

    override def get(uri: String)(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[CacheMap] =
      Future.failed(e)
  }
  def apply(key: String) = new MockedSessionCache {
    override private[client] def cacheId(implicit hc: HeaderCarrier) = Future.successful("sessionId")
    override lazy val baseUri                                        = "https://on-left"
    override lazy val defaultSource: String                          = source
    override lazy val domain: String                                 = "keystore"

    override def get(uri: String)(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[CacheMap] =
      ???
    override def put[T](
      uri: String,
      body: T)(implicit hc: HeaderCarrier, wts: Writes[T], executionContext: ExecutionContext): Future[CacheMap] =
      Future.successful(CacheMap("sessionId", Map(key -> wts.writes(body))))
  }
}

object ShortLivedCachingForTest {

  private val source = "aSource"

  def apply(map: CacheMap) = new ShortLivedHttpCaching with MockedSessionCache {
    override lazy val defaultSource: String = source
    override lazy val baseUri               = "https://on-right"
    override lazy val domain: String        = "save4later"

    override def get(uri: String)(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[CacheMap] =
      Future.successful(map)
    override def put[T](
      uri: String,
      body: T)(implicit hc: HeaderCarrier, wts: Writes[T], executionContext: ExecutionContext): Future[CacheMap] = ???
  }

  def apply(e: Exception) = new ShortLivedHttpCaching with MockedSessionCache {
    override lazy val defaultSource: String = source
    override lazy val baseUri               = "https://on-right"
    override lazy val domain: String        = "save4later"

    override def get(uri: String)(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[CacheMap] =
      Future.failed(e)
  }

  def apply(id: String, key: String) = new ShortLivedHttpCaching with MockedSessionCache {
    override lazy val defaultSource: String = source
    override lazy val baseUri               = "https://on-right"
    override lazy val domain: String        = "save4later"

    override def get(uri: String)(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[CacheMap] =
      ???
    override def put[T](
      uri: String,
      body: T)(implicit hc: HeaderCarrier, wts: Writes[T], executionContext: ExecutionContext): Future[CacheMap] =
      Future.successful(CacheMap(id, Map(key -> wts.writes(body))))
  }
}
