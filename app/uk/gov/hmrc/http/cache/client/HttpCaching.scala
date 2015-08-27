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
import uk.gov.hmrc.play.audit.http.config.{AuditingConfig, LoadAuditingConfig}
import uk.gov.hmrc.play.config.{AppName, ServicesConfig, RunMode}
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.ws._
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector => AuditConnection}

import scala.concurrent.ExecutionContext.Implicits.global
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
  val httpGet : HttpGet
  val httpPut : HttpPut
  val httpDelete : HttpDelete

  def get(uri: String)(implicit hc: HeaderCarrier): Future[CacheMap] = httpGet.GET[CacheMap](uri)

  def put[T](uri: String, body: T)(implicit hc: HeaderCarrier, wts: Writes[T]): Future[CacheMap] =
    httpPut.PUT[T, CacheMap](uri, body)

  def delete(uri: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = httpDelete.DELETE(uri)

}

trait HttpCaching extends CachingVerbs {

  lazy val defaultSource: String = ???
  lazy val baseUri: String = ???
  lazy val domain: String = ???

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

  private[client] def cacheId(implicit hc: HeaderCarrier) = hc.sessionId.getOrElse(throw new RuntimeException("Could not find sessionId in HeaderCarrier")).value

  def cache[A](formId: String, body: A)(implicit wts: Writes[A], hc: HeaderCarrier): Future[CacheMap] =
    cache(defaultSource, cacheId, formId, body)

  def fetch()(implicit hc: HeaderCarrier): Future[Option[CacheMap]] = fetch(defaultSource, cacheId)

  def fetchAndGetEntry[T](key: String)(implicit hc: HeaderCarrier, rds: Reads[T]): Future[Option[T]] = fetchAndGetEntry(defaultSource, cacheId, key)

  def remove()(implicit hc: HeaderCarrier): Future[HttpResponse] = delete(buildUri(defaultSource, cacheId))
}

object SessionCache extends SessionCache with ServicesConfig with AppName {

  override lazy val defaultSource: String = appName

  override lazy val baseUri: String = s"${baseUrl("cachable.session-cache")}"

  override lazy val domain: String = s"${
    getConfString("cachable.session-cache.domain",
      throw new RuntimeException(s"Could not find config 'cachable.session-cache.domain'"))
  }"

  override lazy val httpGet = new WSGet with AppName with Auditing
  override lazy val httpPut = new WSPut with AppName with Auditing
  override lazy val httpDelete = new WSDelete with AppName with Auditing
}


/**
 * A cache client with a defined short lived TTL, i.e. longer than a user's browser session
 */
trait ShortLivedHttpCaching extends HttpCaching {

  override lazy val httpGet: HttpGet = WSHttp
  override lazy val httpPut: HttpPut = WSHttp
  override lazy val httpDelete: HttpDelete = WSHttp

  def cache[A](cacheId: String, formId: String, body: A)(implicit hc: HeaderCarrier, wts: Writes[A]): Future[CacheMap] =
    cache(defaultSource, cacheId, formId, body)

  def fetch(cacheId: String)(implicit hc: HeaderCarrier): Future[Option[CacheMap]] =
    fetch(defaultSource, cacheId)

  def fetchAndGetEntry[T](cacheId: String, key: String)(implicit hc: HeaderCarrier, rds: Reads[T]): Future[Option[T]] =
    fetchAndGetEntry(defaultSource, cacheId, key)

  def remove(cacheId: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = delete(buildUri(defaultSource, cacheId))
}

private[client] object ShortLivedHttpCaching extends ShortLivedHttpCaching with ServicesConfig with AppName {
  override lazy val defaultSource: String = appName

  override lazy val baseUri: String = s"${baseUrl("cachable.short-lived-cache")}"

  override lazy val domain: String = s"${
    getConfString("cachable.short-lived-cache.domain",
      throw new RuntimeException(s"Could not find config 'cachable.short-lived-cache.domain'"))
  }"
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

trait Auditing {
  val auditConnector: AuditConnection = AuditConnector
}

object AuditConnector extends AuditConnection with AppName with RunMode {
  override val auditingConfig: AuditingConfig = LoadAuditingConfig(s"$env.auditing")
}

object WSHttp extends WSGet with WSPut with WSPost with WSDelete with AppName with RunMode {
  override val auditConnector: AuditConnection = AuditConnector
}

