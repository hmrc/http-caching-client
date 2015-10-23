/*
 * Copyright 2015 HM Revenue & Customs
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

import play.api.data.validation.ValidationError
import play.api.libs.json._
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future


case class CacheMap(id: String, data: Map[String, JsValue]) {

  def getEntry[T](key: String)(implicit fjs: Reads[T]): Option[T] = {
    data.get(key).map(json => json.validate[T].fold(
      errs => throw new KeyStoreEntryValidationException(key, json, CacheMap.getClass, errs),
      valid => valid))
  }
}

object CacheMap {
  implicit val formats = Json.format[CacheMap]
}

trait CachingVerbs {
  def http : HttpGet with HttpPut with HttpDelete

  def get(uri: String)(implicit hc: HeaderCarrier): Future[CacheMap] = http.GET[CacheMap](uri)

  def put[T](uri: String, body: T)(implicit hc: HeaderCarrier, wts: Writes[T]): Future[CacheMap] =
    http.PUT[T, CacheMap](uri, body)

  def delete(uri: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = http.DELETE(uri)

}

trait HttpCaching extends CachingVerbs {

  def defaultSource: String
  def baseUri: String
  def domain: String

  def cache[A](source: String, cacheId: String, formId: String, body: A)(implicit wts: Writes[A], hc: HeaderCarrier): Future[CacheMap] = {
    put[A](buildUri(source, cacheId) + s"/data/$formId", body)
  }

  def fetch(source: String, cacheId: String)(implicit hc: HeaderCarrier): Future[Option[CacheMap]] = {
    get(buildUri(source, cacheId)).map(Some(_)).recover {
      case e: NotFoundException => None
    }
  }

  def fetchAndGetEntry[T](source: String, cacheId: String, key: String)(implicit hc: HeaderCarrier, rds: Reads[T]): Future[Option[T]] =
    fetch(source, cacheId).map(_.flatMap(_.getEntry[T](key)))

  protected def buildUri(source: String, id: String): String = s"$baseUri/$domain/$source/$id"
}

/**
 * The session based client is the default
 */
trait SessionCache extends HttpCaching {

  private val noSession = Future.failed[String](NoSessionException)

  private[client] def cacheId(implicit hc: HeaderCarrier): Future[String] =
    hc.sessionId.fold(noSession)(c => Future.successful(c.value))

  def cache[A](formId: String, body: A)(implicit wts: Writes[A], hc: HeaderCarrier): Future[CacheMap] =
    for {
      c <- cacheId
      result <- cache(defaultSource, c, formId, body)
    } yield result

  def fetch()(implicit hc: HeaderCarrier): Future[Option[CacheMap]] =
    for {
      c <- cacheId
      result <- fetch(defaultSource, c)
    } yield result

  def fetchAndGetEntry[T](key: String)(implicit hc: HeaderCarrier, rds: Reads[T]): Future[Option[T]] =
    for {
      c <- cacheId
      result <- fetchAndGetEntry(defaultSource, c, key)
    } yield result

  def remove()(implicit hc: HeaderCarrier): Future[HttpResponse] =
    for {
      c <- cacheId
      result <- delete(buildUri(defaultSource, c))
    } yield result
}

/**
 * A cache client with a defined short lived TTL, i.e. longer than a user's browser session
 */
trait ShortLivedHttpCaching extends HttpCaching {

  def cache[A](cacheId: String, formId: String, body: A)(implicit hc: HeaderCarrier, wts: Writes[A]): Future[CacheMap] =
    cache(defaultSource, cacheId, formId, body)

  def fetch(cacheId: String)(implicit hc: HeaderCarrier): Future[Option[CacheMap]] =
    fetch(defaultSource, cacheId)

  def fetchAndGetEntry[T](cacheId: String, key: String)(implicit hc: HeaderCarrier, rds: Reads[T]): Future[Option[T]] =
    fetchAndGetEntry(defaultSource, cacheId, key)

  def remove(cacheId: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = delete(buildUri(defaultSource, cacheId))
}


class KeyStoreEntryValidationException(
                                        val key: String,
                                        val invalidJson: JsValue,
                                        val readingAs: Class[_],
                                        val errors: Seq[(JsPath, Seq[ValidationError])]) extends Exception {
  override def getMessage: String = {
    s"KeyStore entry for key '$key' was '${Json.stringify(invalidJson)}'. Attempt to convert to ${readingAs.getName} gave errors: $errors"
  }
}
