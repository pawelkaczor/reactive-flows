/*
 * Copyright 2015 Heiko Seeberger
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

package de.heikoseeberger.reactiveflows

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.RouteTest
import akka.http.scaladsl.testkit.TestFrameworkInterface.Scalatest
import akka.testkit.{ EventFilter, TestProbe }
import org.scalatest.{ Matchers, WordSpec }

class HttpServiceSpec extends WordSpec with Matchers with RouteTest with Scalatest with RequestBuilding {
  import HttpService._

  "The HttpService route" should {
    "send itself a Stop upon a 'DELETE /' and respond with OK" in {
      val httpService = TestProbe()

      val request = Delete()
      request ~> route(httpService.ref) ~> check {
        response.status shouldBe StatusCodes.OK
      }

      httpService.expectMsg(Stop)
    }

    "respond with OK and index.html upon a 'GET /'" in {
      val httpService = TestProbe()

      val request = Get()
      request ~> route(httpService.ref) ~> check {
        response.status shouldBe StatusCodes.OK
        responseAs[String].trim shouldBe "test"
      }
    }

    "respond with OK and index.html upon a 'GET /index.html'" in {
      val httpService = TestProbe()

      val request = Get("/index.html")
      request ~> route(httpService.ref) ~> check {
        response.status shouldBe StatusCodes.OK
        responseAs[String].trim shouldBe "test"
      }
    }
  }

  "Creating a HttpService" should {
    "either successfully bind to a socket or terminate" in {
      val interface = "127.0.0.1"
      val port = 9876
      val probe = TestProbe()

      EventFilter.info(occurrences = 1, pattern = s"Listening on.*$interface:$port").intercept {
        system.actorOf(HttpService.props(interface, port))
      }

      val httpService = probe.watch(system.actorOf(HttpService.props(interface, port)))
      probe.expectTerminated(httpService)
    }
  }

  "Sending Stop to a HttpService" should {
    "result in terminating" in {
      val probe = TestProbe()

      val httpService = probe.watch(system.actorOf(HttpService.props("127.0.0.1", 9876)))
      httpService ! Stop
      probe.expectTerminated(httpService)
    }
  }
}
