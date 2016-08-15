/*
 * Copyright 2016 HM Revenue & Customs
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

import play.api.libs.json._
import uk.gov.hmrc.crypto.json.{JsonDecryptor, JsonEncryptor}
import uk.gov.hmrc.crypto.{CompositeSymmetricCrypto, Protected}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.HttpResponse

import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import scala.concurrent.Future


trait ShortLivedCache extends CacheUtil {

  implicit val crypto: CompositeSymmetricCrypto

  def shortLiveCache: ShortLivedHttpCaching

  def cache[A](cacheId: String, formId: String, body: A)(implicit hc: HeaderCarrier, wts: Writes[A]): Future[CacheMap] = {
    val protectd = Protected(body)
    val encryptionFormat = new JsonEncryptor()
    val fm = shortLiveCache.cache(cacheId, formId, protectd)(hc, encryptionFormat)
    fm.map(cm => new CryptoCacheMap(cm))
  }

  def fetch(cacheId: String)(implicit hc: HeaderCarrier): Future[Option[CacheMap]] = {
    val fm = shortLiveCache.fetch(cacheId)
    fm.map(om => om.map(cm => new CryptoCacheMap(cm)))
  }

  def fetchAndGetEntry[T](cacheId: String, key: String)(implicit hc: HeaderCarrier, rds: Reads[T]): Future[Option[T]] =
    try {
      val decryptionFormat = new JsonDecryptor()
      val encrypted: Future[Option[Protected[T]]] = shortLiveCache.fetchAndGetEntry(cacheId, key)(hc, decryptionFormat)
      encrypted.map(op => convert(op))
    } catch {
      case e: SecurityException => throw CachingException(s"Failed to fetch a decrypted entry by cacheId:$cacheId and key:$key", e)
    }

  def remove(cacheId: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = shortLiveCache.remove(cacheId)
}

trait CacheUtil {
  def convert[T](entry: Option[Protected[T]]): Option[T] =
    entry.map(e => e.decryptedValue)
}

class CryptoCacheMap(cm: CacheMap)(implicit crypto: CompositeSymmetricCrypto)
  extends CacheMap(cm.id, cm.data) with CacheUtil {

  override def getEntry[T](key: String)(implicit fjs: Reads[T]): Option[T] =
    try {
      val decryptionFormat = new JsonDecryptor()
      val encryptedEntry = cm.getEntry(key)(decryptionFormat)
      convert(encryptedEntry)
    } catch {
      case e: SecurityException => throw CachingException(s"Failed to fetch a decrypted entry by key:$key", e)
    }

}
