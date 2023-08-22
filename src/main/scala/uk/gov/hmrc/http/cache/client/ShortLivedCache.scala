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

import play.api.libs.json._
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, Sensitive}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

trait ShortLivedCache extends CacheUtil {

  implicit val crypto: Decrypter with Encrypter

  def shortLiveCache: ShortLivedHttpCaching

  def cache[A](
    cacheId: String,
    formId : String,
    body   : A
  )(implicit
    hc : HeaderCarrier,
    wts: Writes[A],
    ec : ExecutionContext
  ): Future[CacheMap] = {
    val protectd         = SensitiveA(body)
    val encryptionFormat = JsonEncryption.sensitiveEncrypter[A, SensitiveA[A]]
    shortLiveCache
      .cache(cacheId, formId, protectd)(hc, encryptionFormat, ec)
      .map(cm => new CryptoCacheMap(cm))
  }

  def fetch(
    cacheId: String
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[CacheMap]] =
    shortLiveCache
      .fetch(cacheId)
      .map(_.map(cm => new CryptoCacheMap(cm)))

  def fetchAndGetEntry[A](
    cacheId: String,
    key    : String
  )(implicit
    hc : HeaderCarrier,
    rds: Reads[A],
    ec : ExecutionContext
  ): Future[Option[A]] =
    try {
      val decryptionFormat = JsonEncryption.sensitiveDecrypter[A, SensitiveA[A]](SensitiveA.apply)
      shortLiveCache
        .fetchAndGetEntry(cacheId, key)(hc, decryptionFormat, ec)
        .map(convert)
    } catch {
      case e: SecurityException =>
        throw CachingException(s"Failed to fetch a decrypted entry by cacheId:$cacheId and key:$key", e)
    }

  def remove(cacheId: String)(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[HttpResponse] =
    shortLiveCache.remove(cacheId)
}

trait CacheUtil {
  def convert[A](entry: Option[Sensitive[A]]): Option[A] =
    entry.map(_.decryptedValue)
}

class CryptoCacheMap(cm: CacheMap)(implicit crypto: Decrypter with Encrypter)
    extends CacheMap(cm.id, cm.data)
    with CacheUtil {

  override def getEntry[A](key: String)(implicit fjs: Reads[A]): Option[A] =
    try {
      val decryptionFormat = JsonEncryption.sensitiveDecrypter[A, SensitiveA[A]](SensitiveA.apply)
      val encryptedEntry   = cm.getEntry(key)(decryptionFormat)
      convert(encryptedEntry)
    } catch {
      case e: SecurityException => throw CachingException(s"Failed to fetch a decrypted entry by key:$key", e)
    }
}

private case class SensitiveA[A](override val decryptedValue: A) extends Sensitive[A]
