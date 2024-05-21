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

import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{Format, JsValue, Json, Reads, Writes}
import uk.gov.hmrc.crypto._
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class ShortLivedCacheSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with OptionValues
     with ScalaFutures
     with IntegrationPatience {

  implicit val hc: HeaderCarrier =
    HeaderCarrier()

  "ShortLivedCache" should {
    import uk.gov.hmrc.http.cache.client.FormOnPage3.formats

    val formOnPage = FormOnPage3("abdu", field2 = false)

    "encrypt and cache a given data" in new Setup {
      val cm  = shortLivedCache.cache("save4later", "form1", FormOnPage3("me", true))(hc, FormOnPage3.formats, global).futureValue
      val cm2 = shortLivedCache.fetch("save4later").futureValue
      cm2 should be(Some(cm))
    }

    "encrypt the data and fetch by key" in new Setup {
      shortLivedCache.cache("save4later", "form2", formOnPage).futureValue
      val form = shortLivedCache.fetchAndGetEntry[FormOnPage3]("save4later", "form2")(hc, FormOnPage3.formats, global).futureValue
      form should be(Some(formOnPage))
    }

    "return decrypted cache entry" in new Setup {
      val cm   = shortLivedCache.cache("save4later", "form3", formOnPage).futureValue
      val form = cm.getEntry[FormOnPage3]("form3")
      form should be(Some(formOnPage))
    }

    "not return any uncached entry when the key is missing" in new Setup {
      val cm   = shortLivedCache.cache("save4later", "form4", formOnPage).futureValue
      val form = cm.getEntry[FormOnPage3]("form6")
      form should be(empty)
    }

    "not return any uncached entry when fetched" in new Setup {
      shortLivedCache.cache("save4later", "form5", formOnPage).futureValue
      val form = shortLivedCache.fetchAndGetEntry[FormOnPage3]("save4later", "form7")(hc, FormOnPage3.formats, global).futureValue
      form should be(empty)
    }

    "fetch non existing cache" in new Setup {
      val emptyCache = shortLivedCache.fetch("non-existing-item").futureValue
      emptyCache should be(empty)
    }

    "capture crypto exception during getEntry" in new Setup {
      intercept[CachingException] {
        shortLivedCache.cache("exception-cache-id", "exception-key", formOnPage).futureValue
        val cm = shortLivedCache.fetch("exception-cache-id").futureValue
        cm.map(f => f.getEntry("exception-key"))
      }
    }
  }

  trait Setup { outer =>
    val baseUri       = "https://on-left"
    val defaultSource = "aSource"
    val domain        = "keystore"

    val shortLivedHttpCaching = new ShortLivedHttpCaching {
      override def baseUri      : String = outer.baseUri
      override def defaultSource: String = outer.defaultSource
      override def domain       : String = outer.domain

      override val httpClientV2 = mock[HttpClientV2]

      override val cachingVerbs: CachingVerbs = new CachingVerbs {
        private val map =
          mutable.HashMap[String, CacheMap]()

        override def get(
          uri: String
        )(implicit
          hc: HeaderCarrier,
          ec: ExecutionContext
        ): Future[CacheMap] = {
          val Seq(domain, source, id) = uri.stripPrefix(baseUri + "/").split("/").toSeq
          map.get(id).fold[Future[CacheMap]](Future.failed(UpstreamErrorResponse("Not found", 404)))(Future.successful _)
        }

        override def put[T](
          uri : String,
          body: T
        )(implicit
          hc : HeaderCarrier,
          wts: Writes[T],
          ec : ExecutionContext
        ): Future[CacheMap] = {
          val jsValue  = wts.writes(body)
          val Seq(domain, source, id, data, formId) = uri.stripPrefix(baseUri + "/").split("/").toSeq
          val cacheMap = new TestCacheMap(id, Map(formId -> jsValue))
          map.put(id, cacheMap)
          Future.successful(cacheMap)
        }

        override def delete(
          uri: String
        )(implicit
          hc: HeaderCarrier,
          ec: ExecutionContext
        ): Future[Unit] = {
          map.clear()
          Future.unit
        }
      }
    }

    val shortLivedCache = new ShortLivedCache {
      val shortLiveCache: ShortLivedHttpCaching = shortLivedHttpCaching
      val crypto                                = TestCrypto
    }
  }
}

object FormOnPage3 {
  implicit val formats: Format[FormOnPage3] = Json.format[FormOnPage3]
}

case class FormOnPage3(field1: String, field2: Boolean)

object TestCrypto extends Encrypter with Decrypter {

  override def encrypt(value: PlainContent): Crypted =
    value match {
      case PlainText(text) => Crypted(text)
      case _               => throw new RuntimeException(s"Unable to encrypt unknown message type: $value")
    }

  override def decrypt(crypted: Crypted): PlainText =
    PlainText(crypted.value)

  override def decryptAsBytes(crypted: Crypted): PlainBytes =
    PlainBytes(crypted.value.getBytes)
}

class TestCacheMap(override val id: String, override val data: Map[String, JsValue]) extends CacheMap(id, data) {
  override def getEntry[T](key: String)(implicit fjs: Reads[T]): Option[T] =
    if (key == "exception-key") throw new SecurityException("Couldn't decrypt entry")
    else
      super.getEntry(key)
}
