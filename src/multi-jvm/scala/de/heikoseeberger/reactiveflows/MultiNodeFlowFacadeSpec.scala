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

import akka.cluster.Cluster
import akka.cluster.ddata.DistributedData
import akka.remote.testkit.{ MultiNodeConfig, MultiNodeSpec }
import akka.testkit.{ TestDuration, TestProbe }
import com.typesafe.config.ConfigFactory
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import scala.concurrent.duration.DurationInt

object FlowFacadeSpecConfig extends MultiNodeConfig {

  val List(node1, node2) = List(2551, 2552).map(node)

  private def node(port: Int) = {
    commonConfig(ConfigFactory.load())
    val node = role(port.toString)
    nodeConfig(node)(ConfigFactory.parseString(
      s"""|akka.remote.netty.tcp.hostname = "127.0.0.1"
          |akka.remote.netty.tcp.port     = $port
          |akka.cluster.seed-nodes        = ["akka.tcp://MultiNodeFlowFacadeSpec@127.0.0.1:2551"]
          |""".stripMargin
    ))
    node
  }
}

class FlowFacadeSpecMultiJvmNode1 extends MultiNodeFlowFacadeSpec
class FlowFacadeSpecMultiJvmNode2 extends MultiNodeFlowFacadeSpec

abstract class MultiNodeFlowFacadeSpec extends MultiNodeSpec(FlowFacadeSpecConfig)
    with WordSpecLike with Matchers with BeforeAndAfterAll {

  import FlowFacadeSpecConfig._

  "FlowFacade" should {
    "work as expected in a cluster" in {
      within(10.seconds.dilated) {
        awaitAssert {
          Cluster(system).state.members.size shouldBe 2
        }
      }

      enterBarrier("ready")

      val flowFacade = system.actorOf(FlowFacade.props(system.deadLetters, DistributedData(system).replicator))
      runOn(node1) {
        flowFacade ! FlowFacade.AddFlow("Akka")
      }
      runOn(node2) {
        val sender = TestProbe()
        implicit val senderRef = sender.ref
        within(10.seconds.dilated) {
          sender.awaitAssert {
            flowFacade ! FlowFacade.GetFlows
            sender.expectMsg(Set(FlowFacade.FlowDescriptor("akka", "Akka")))
          }
        }
      }
    }
  }

  override def initialParticipants = roles.size

  override protected def beforeAll() = {
    super.beforeAll()
    multiNodeSpecBeforeAll()
  }

  override protected def afterAll() = {
    multiNodeSpecAfterAll()
    super.afterAll()
  }
}
