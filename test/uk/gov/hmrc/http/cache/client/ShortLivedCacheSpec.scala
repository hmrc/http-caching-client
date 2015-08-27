package uk.gov.hmrc.http.cache.client

import org.scalatest.concurrent.ScalaFutures
import play.api.Logger
import play.api.libs.json.{JsValue, Json, Reads, Writes}
import uk.gov.hmrc.crypto._
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.collection.mutable
import scala.concurrent.Future


class ShortLivedCacheSpec extends UnitSpec with ScalaFutures with WithFakeApplication {

  implicit val hc = HeaderCarrier()


  "ShortLivedCacheWithCrpto" should {
    import uk.gov.hmrc.http.cache.client.FormOnPage3.formats

    val formOnPage = FormOnPage3("abdu", field2 = false)

    val slcCrypto = new ShortLivedCache {
      val shortLiveCache: ShortLivedHttpCaching = TestShortLiveHttpCaching
      val crypto = TestCrypto
    }

    "encrypt and cache a given data" in {
      val cm = await(slcCrypto.cache("save4later", "form1", FormOnPage3("me", true))(hc, FormOnPage3.formats))
      val cm2 = await(slcCrypto.fetch("save4later"))
      cm2 should be(Some(cm))
    }

    "encrypt the data and fetch by key" in {
      await(slcCrypto.cache("save4later", "form2", formOnPage))
      val form = await(slcCrypto.fetchAndGetEntry[FormOnPage3]("save4later", "form2")(hc, FormOnPage3.formats))
      form should be(Some(formOnPage))
    }

    "return decrypted cache entry" in {
      val cm = await(slcCrypto.cache("save4later", "form3", formOnPage))
      val form = cm.getEntry[FormOnPage3]("form3")
      form should be(Some(formOnPage))
    }

    "not return any uncached entry when the key is missing" in {
      val cm = await(slcCrypto.cache("save4later", "form4", formOnPage))
      val form = cm.getEntry[FormOnPage3]("form6")
      form should be(empty)
    }

    "not return any uncached entry when fetched" in {
      await(slcCrypto.cache("save4later", "form5", formOnPage))
      val form = await(slcCrypto.fetchAndGetEntry[FormOnPage3]("save4later", "form7")(hc, FormOnPage3.formats))
      form should be(empty)
    }

    "fetch non existing cache" in {
      val emptyCache = await(slcCrypto.fetch("non-existing-item"))
      emptyCache should be(empty)
    }

    "capture crypto exception during fetchAndGetEntry" in {
      intercept[CachingException] {
        slcCrypto.fetchAndGetEntry("save4later", "exception-fetch-id")(hc, FormOnPage3.formats)
      }
    }

    "capture crypto exception during getEntry" in {
      intercept[CachingException] {
        await(slcCrypto.cache("exception-cache-id", "exception-key", formOnPage))
        val cm = await(slcCrypto.fetch("exception-cache-id"))
        cm.map(f => f.getEntry("exception-key"))
      }
    }

  }
}

object FormOnPage3 {
  implicit val formats = Json.format[FormOnPage3]
}

case class FormOnPage3(field1: String, field2: Boolean)


object TestCrypto extends CompositeSymmetricCrypto {

  override def encrypt(value: PlainContent): Crypted = value match {
    case PlainText(text) => Crypted(text)
    case _ => throw new RuntimeException(s"Unable to encrypt unknown message type: $value")
  }

  override def decrypt(crypted: Crypted): PlainText = {
    PlainText(crypted.value)
  }

  override protected lazy val currentCrypto: Encrypter with Decrypter = ???
  override protected lazy val previousCryptos: Seq[Decrypter] = ???
}

class TestCacheMap(override val id: String, override val data: Map[String, JsValue]) extends CacheMap(id, data) {
  override def getEntry[T](key: String)(implicit fjs: Reads[T]): Option[T] =
    if (key == "exception-key") throw new SecurityException("Couldn't decrypt entry")
    else
      super.getEntry(key)

}

object TestShortLiveHttpCaching extends ShortLivedHttpCaching {

  import scala.concurrent.ExecutionContext.Implicits.global

  override lazy val defaultSource: String = "save4later"
  override lazy val baseUri: String = "save4later"
  override lazy val domain: String = "save4later"
  val map = mutable.HashMap[String, CacheMap]()

  override def cache[A](cacheId: String, formId: String, body: A)(implicit hc: HeaderCarrier, wts: Writes[A]): Future[CacheMap] = {
    Future {
      val jsValue = wts.writes(body)
      val cacheMap = new TestCacheMap(cacheId, Map(formId -> jsValue))
      map.put(cacheId, cacheMap)
      cacheMap
    }
  }

  override def fetch(cacheId: String)(implicit hc: HeaderCarrier): Future[Option[CacheMap]] = {
    val om = map.get(cacheId)
    Future.successful(om)
  }

  override def fetchAndGetEntry[T](cacheId: String, key: String)(implicit hc: HeaderCarrier, rds: Reads[T]): Future[Option[T]] =
    if (key == "exception-fetch-id") {
      throw new SecurityException("Unable to decrypt entry")
    } else
      Future {
        val cm = map.get(cacheId)
        cm.flatMap { cm =>
          val e = cm.getEntry[T](key)
          Logger.info(s"Fetching entry:$e from cache by key:$key with reader:$rds")
          e
        }
      }
}