/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{verify, when}
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json._
import uk.gov.hmrc.http.{HeaderCarrier, SessionId, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class HttpCachingClientSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with MockitoSugar
     with OptionValues {

  val sessionId = "ksc-session-id"

  implicit val hc: HeaderCarrier =
    new HeaderCarrier(sessionId = Some(SessionId(sessionId)))

  "SessionCache" should {
    val id = "httpSessionId"

    "fetch a map by id" in new SessionCacheSetup {
      val data = CacheMap(id, Map("form1" -> Json.obj("field1" -> "value1")))

      when(mockCachingVerbs.get(any[String])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(data))

      sessionCache.fetch().futureValue.value shouldBe data

      verify(mockCachingVerbs).get(eqTo(s"$baseUri/$domain/$defaultSource/$sessionId"))(any[HeaderCarrier], any[ExecutionContext])
    }

    "return None if the map is not found" in new SessionCacheSetup {
      when(mockCachingVerbs.get(any[String])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(UpstreamErrorResponse("Not found", 404)))

      sessionCache.fetch().futureValue shouldBe None
    }

    "fetch an entry in map" in new SessionCacheSetup {
      val data  = CacheMap(id, Map("form1" -> Json.obj("field1" -> "value1")))

      when(mockCachingVerbs.get(any[String])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(data))

      implicit val formats: Format[Field1] = Json.format[Field1]

      sessionCache.fetchAndGetEntry("form1").futureValue.value shouldBe Field1("value1")
    }

    "store an entry" in new SessionCacheSetup {
      val formId = "form1"
      val data   = Field1("value1")

      when(mockCachingVerbs.put(any[String], any[Field1])(any[HeaderCarrier], any[Writes[Field1]], any[ExecutionContext]))
        .thenReturn(Future.successful(CacheMap("sessionId", Map(id -> Json.toJson(data)))))

      val cacheMap = sessionCache.cache[Field1](formId, data).futureValue

      cacheMap.data shouldBe Map(id -> new JsObject(Map("field1" -> JsString("value1"))))

      verify(mockCachingVerbs).put(eqTo(s"$baseUri/$domain/$defaultSource/$sessionId/data/$formId"), eqTo(data))(any[HeaderCarrier], any[Writes[Field1]], any[ExecutionContext])
    }

    "delete an entry" in new SessionCacheSetup {
      when(mockCachingVerbs.delete(any[String])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.unit)

      sessionCache.remove().futureValue

      verify(mockCachingVerbs).delete(eqTo(s"$baseUri/$domain/$defaultSource/$sessionId"))(any[HeaderCarrier], any[ExecutionContext])
    }
  }

  "CacheMap" should {
    val id = "httpSessionId"

    "extract entries" in {
      val cacheMap =
        CacheMap(
          id,
          Map(
            "form1" -> Json.obj("field1" -> "value1"),
            "form2" -> Json.obj("field2" -> true)
          )
        )

      implicit val formats: Format[Field1] = Json.format[Field1]

      cacheMap.getEntry[Field1]("form1").value shouldBe Field1("value1")
    }

    "return an exception for conversions errors" in {
      val cacheMap =
        CacheMap(
          id,
          Map("form1" -> Json.obj("field1" -> true))
        )

      implicit val formats: Format[Field1] = Json.format[Field1]

      intercept[KeyStoreEntryValidationException] {
        cacheMap.getEntry[Field1]("form1")
      }
    }
  }

  "ShortLivedHttpCaching" should {
    val id = "explicitlySetId"

    "fetch a map by id" in new ShortLivedHttpCachingSetup {
      val data  = CacheMap(id, Map("form1" -> Json.obj("field1" -> "value1")))

      when(mockCachingVerbs.get(any[String])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(data))

      shortLivedHttpCaching.fetch(id).futureValue.value shouldBe data

      verify(mockCachingVerbs).get(eqTo(s"$baseUri/$domain/$defaultSource/$id"))(any[HeaderCarrier], any[ExecutionContext])
    }

    "store an entry" in new ShortLivedHttpCachingSetup {
      val formId = "form1"
      val data   = Field1("value1")

      when(mockCachingVerbs.put(any[String], any[Field1])(any[HeaderCarrier], any[Writes[Field1]], any[ExecutionContext]))
        .thenReturn(Future.successful(CacheMap("sessionId", Map(id -> Json.toJson(data)))))

      val cacheMap = shortLivedHttpCaching.cache[Field1](id, formId, data).futureValue

      cacheMap.data shouldBe Map(id -> new JsObject(Map("field1" -> JsString("value1"))))

      verify(mockCachingVerbs).put(eqTo(s"$baseUri/$domain/$defaultSource/$id/data/$formId"), eqTo(data))(any[HeaderCarrier], any[Writes[Field1]], any[ExecutionContext])
    }
  }

  trait SessionCacheSetup { outer =>
    val baseUri       = "https://on-left"
    val defaultSource = "aSource"
    val domain        = "keystore"

    val mockCachingVerbs = mock[CachingVerbs]

    val sessionCache = new SessionCache {
      override def baseUri      : String = outer.baseUri
      override def defaultSource: String = outer.defaultSource
      override def domain       : String = outer.domain

      override val httpClientV2 = mock[HttpClientV2]

      override val cachingVerbs: CachingVerbs = mockCachingVerbs
    }
  }

  trait ShortLivedHttpCachingSetup { outer =>
    val baseUri       = "https://on-left"
    val defaultSource = "aSource"
    val domain        = "keystore"

    val mockCachingVerbs = mock[CachingVerbs]

    val shortLivedHttpCaching = new ShortLivedHttpCaching {
      override def baseUri      : String = outer.baseUri
      override def defaultSource: String = outer.defaultSource
      override def domain       : String = outer.domain
      override val httpClientV2 = mock[HttpClientV2]

      override val cachingVerbs: CachingVerbs = mockCachingVerbs
    }
  }
}

case class Field1(field1: String)

object Field1 {
  implicit val formats: Format[Field1] = Json.format[Field1]
}

object FormOnPage1 {
  implicit val formats: Format[FormOnPage1] = Json.format[FormOnPage1]
}

case class FormOnPage1(field1: String, field2: Boolean)
case class FormOnPage2(field1: Int)
