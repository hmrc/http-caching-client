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
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

case class CacheMap(id: String, data: Map[String, JsValue]) {

  def getEntry[T](key: String)(implicit fjs: Reads[T]): Option[T] =
    data
      .get(key)
      .map(json =>
        json
          .validate[T]
          .fold(
            errors => throw new KeyStoreEntryValidationException(key, json, CacheMap.getClass, errors),
            valid  => valid
          )
      )
}

object CacheMap {
  implicit val formats: Format[CacheMap] = Json.format[CacheMap]
}

trait HttpCaching {

  def defaultSource: String
  def baseUri: String
  def domain: String

  def httpClientV2: HttpClientV2

  protected def cachingVerbs: CachingVerbs =
    new DefaultCachingVerbs(httpClientV2)

  def cache[A](
    source : String,
    cacheId: String,
    formId : String,
    body   : A
  )(implicit
    wts: Writes[A], hc: HeaderCarrier, ec: ExecutionContext
  ): Future[CacheMap] =
    cachingVerbs.put[A](buildUri(source, cacheId) + s"/data/$formId", body)
      .map { res => println(s"put($source, $cacheId, $formId, $body) returns $res"); res }

  def fetch(
    source : String,
    cacheId: String
  )(implicit
    hc: HeaderCarrier, ec: ExecutionContext
  ): Future[Option[CacheMap]] =
    cachingVerbs.get(buildUri(source, cacheId)).map(Some(_)).recover {
      case UpstreamErrorResponse.WithStatusCode(404) => None
    }

  def fetchAndGetEntry[T](
    source : String,
    cacheId: String,
    key    : String
  )(implicit
    hc: HeaderCarrier, rds: Reads[T], ec: ExecutionContext
  ): Future[Option[T]] =
    fetch(source, cacheId).map(_.flatMap(_.getEntry[T](key)))

  protected def buildUri(source: String, id: String): String =
    s"$baseUri/$domain/$source/$id"
}

/**
  * The session based client is the default
  */
trait SessionCache extends HttpCaching {

  private val noSession = Future.failed[String](NoSessionException)

  private[client] def cacheId(implicit hc: HeaderCarrier): Future[String] =
    hc.sessionId.fold(noSession)(c => Future.successful(c.value))

  def cache[A](
    formId: String,
    body  : A
  )(implicit
    wts: Writes[A], hc: HeaderCarrier, ec: ExecutionContext
  ): Future[CacheMap] =
    for {
      c      <- cacheId
      result <- cache(defaultSource, c, formId, body)
    } yield result

  def fetch()(
    implicit hc: HeaderCarrier, ec: ExecutionContext
  ): Future[Option[CacheMap]] =
    for {
      c      <- cacheId
      result <- fetch(defaultSource, c)
    } yield result

  def fetchAndGetEntry[T](
    key: String
  )(implicit
    hc: HeaderCarrier, rds: Reads[T], ec: ExecutionContext
  ): Future[Option[T]] =
    for {
      c      <- cacheId
      result <- fetchAndGetEntry(defaultSource, c, key)
    } yield result

  def remove()(
    implicit hc: HeaderCarrier, ec: ExecutionContext
  ): Future[Unit] =
    for {
      c      <- cacheId
      result <- cachingVerbs.delete(buildUri(defaultSource, c))
    } yield result
}

/**
  * A cache client with a defined short lived TTL, i.e. longer than a user's browser session
  */
trait ShortLivedHttpCaching extends HttpCaching {

  def cache[A](
    cacheId: String,
    formId : String,
    body   : A
  )(implicit
    hc: HeaderCarrier, wts: Writes[A], ec: ExecutionContext
  ): Future[CacheMap] =
    cache(defaultSource, cacheId, formId, body)

  def fetch(
    cacheId: String
  )(implicit
    hc: HeaderCarrier, ec: ExecutionContext
  ): Future[Option[CacheMap]] =
    fetch(defaultSource, cacheId)

  def fetchAndGetEntry[T](
    cacheId: String,
    key    : String
  )(implicit
    hc: HeaderCarrier, rds: Reads[T], ec: ExecutionContext
  ): Future[Option[T]] =
    fetchAndGetEntry(defaultSource, cacheId, key)

  def remove(
    cacheId: String
  )(implicit
    hc: HeaderCarrier, ec: ExecutionContext
  ): Future[Unit] =
    cachingVerbs.delete(buildUri(defaultSource, cacheId))
}


//abstraction for mocking
trait CachingVerbs {

  def get(uri: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CacheMap]

  def put[T](uri: String, body: T)(implicit hc: HeaderCarrier, wts: Writes[T], ec: ExecutionContext): Future[CacheMap]

  def delete(uri: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit]
}

class DefaultCachingVerbs(httpClientV2: HttpClientV2) extends CachingVerbs {
  import play.api.libs.ws.writeableOf_JsValue

  import uk.gov.hmrc.http.HttpReads.Implicits._
  private val legacyRawReads: HttpReads[HttpResponse] =
    throwOnFailure(readEitherOf(readRaw))

  def get(uri: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CacheMap] =
    httpClientV2.get(new URL(uri)).execute[CacheMap]

  def put[T](uri : String, body: T)(implicit hc: HeaderCarrier, wts: Writes[T], ec: ExecutionContext): Future[CacheMap] =
    httpClientV2.put(new URL(uri)).withBody(Json.toJson(body)).execute[CacheMap]

  def delete(uri: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    httpClientV2.delete(new URL(uri)).execute[HttpResponse](legacyRawReads, implicitly[ExecutionContext])
      .map(_ => ())
}
