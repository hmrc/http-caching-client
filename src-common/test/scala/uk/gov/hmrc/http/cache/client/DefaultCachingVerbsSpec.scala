/*
 * Copyright 2024 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{verify => _, _}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}

import scala.concurrent.ExecutionContext.Implicits.global

class DefaultCachingVerbsSpec
  extends AnyWordSpec
     with Matchers
     with WireMockSupport
     with HttpClientV2Support
     with ScalaFutures
     with IntegrationPatience {

  val defaultCachingVerbs = new DefaultCachingVerbs(httpClientV2)

  "DefaultCachingVerbs" should {
    "make get" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()

      wireMockServer.stubFor(
        WireMock.get(urlEqualTo("/"))
          .willReturn(aResponse().withBody("""{"id": "id1", "data": {"form1": {"field1": "value1"}}}""").withStatus(200))
      )

      val cacheMap: CacheMap =
        defaultCachingVerbs
          .get(s"$wireMockUrl/")
          .futureValue

      cacheMap shouldBe CacheMap("id1", Map("form1" -> Json.obj("field1" -> "value1")))
    }

    "make put" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()

      wireMockServer.stubFor(
        WireMock.put(urlEqualTo("/"))
          .willReturn(aResponse().withBody("""{"id": "id1", "data": {"form1": {"field1": "value1"}}}""").withStatus(200))
      )

      val body = "String"

      val cacheMap: CacheMap =
        defaultCachingVerbs
          .put(s"$wireMockUrl/", body)
          .futureValue

      cacheMap shouldBe CacheMap("id1", Map("form1" -> Json.obj("field1" -> "value1")))

      wireMockServer.verify(
        putRequestedFor(urlEqualTo("/"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withRequestBody(equalTo("\"String\""))
      )
    }

    "make delete" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()

      wireMockServer.stubFor(
        WireMock.delete(urlEqualTo("/"))
          .willReturn(aResponse().withStatus(204))
      )

      defaultCachingVerbs
        .delete(s"$wireMockUrl/")
        .futureValue

      wireMockServer.verify(
        deleteRequestedFor(urlEqualTo("/"))
      )
    }

    "propagate error for failed delete" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()

      wireMockServer.stubFor(
        WireMock.delete(urlEqualTo("/"))
          .willReturn(aResponse().withStatus(500))
      )

      defaultCachingVerbs
        .delete(s"$wireMockUrl/")
        .failed
        .futureValue shouldBe a[UpstreamErrorResponse]

      wireMockServer.verify(
        deleteRequestedFor(urlEqualTo("/"))
      )
    }
  }
}
