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

import akka.actor.{ Actor, ActorRef, Props }
import akka.cluster.Cluster
import akka.cluster.ddata.{ LWWMap, LWWMapKey, Replicator }
import akka.cluster.pubsub.DistributedPubSubMediator
import java.net.URLEncoder

object FlowFacade {

  sealed trait FlowEvent

  case object GetFlows
  case class FlowDescriptor(name: String, label: String)

  case class AddFlow(label: String)
  case class FlowAdded(flowDescriptor: FlowDescriptor) extends FlowEvent
  case class FlowExists(label: String)

  case class RemoveFlow(name: String)
  case class FlowRemoved(name: String) extends FlowEvent
  case class FlowUnknown(name: String)

  case class GetMessages(flowName: String)
  case class AddMessage(flowName: String, text: String)

  // $COVERAGE-OFF$
  final val Name = "flow-facade"

  final val FlowEventKey = "flow-events"
  // $COVERAGE-ON$

  private[reactiveflows] val flowReplicatorKey = LWWMapKey[FlowDescriptor]("flows")

  private val updateFlowData = Replicator.Update(flowReplicatorKey, LWWMap.empty[FlowDescriptor], Replicator.WriteLocal) _

  def props(mediator: ActorRef, replicator: ActorRef, flowShardRegion: ActorRef): Props =
    Props(new FlowFacade(mediator, replicator, flowShardRegion))

  private def labelToName(label: String) = URLEncoder.encode(label.toLowerCase, "UTF-8")
}

class FlowFacade(mediator: ActorRef, replicator: ActorRef, flowShardRegion: ActorRef) extends Actor {
  import FlowFacade._

  private implicit val cluster = Cluster(context.system)

  private var flowsByName = Map.empty[String, FlowDescriptor]

  replicator ! Replicator.Subscribe(flowReplicatorKey, self)

  override def receive = {
    case GetFlows                                          => sender() ! flowsByName.valuesIterator.to[Set]
    case AddFlow(label)                                    => addFlow(label)
    case RemoveFlow(name)                                  => removeFlow(name)
    case GetMessages(flowName)                             => getMessages(flowName)
    case AddMessage(flowName, text)                        => addMessage(flowName, text)
    case changed @ Replicator.Changed(`flowReplicatorKey`) => flowsByName = changed.get(flowReplicatorKey).entries
  }

  protected def forwardToFlow(name: String)(message: Any): Unit = flowShardRegion.forward(name -> message)

  private def addFlow(label: String) = withUnknownFlow(label) { name =>
    val flowDescriptor = FlowDescriptor(name, label)
    flowsByName += name -> flowDescriptor
    replicator ! updateFlowData(_ + (name -> flowDescriptor))
    val flowAdded = FlowAdded(flowDescriptor)
    mediator ! DistributedPubSubMediator.Publish(FlowEventKey, flowAdded)
    sender() ! flowAdded
  }

  private def removeFlow(name: String) = withExistingFlow(name) { name =>
    flowsByName -= name
    replicator ! updateFlowData(_ - name)
    forwardToFlow(name)(Flow.Stop)
    val flowRemoved = FlowRemoved(name)
    mediator ! DistributedPubSubMediator.Publish(FlowEventKey, flowRemoved)
    sender() ! flowRemoved
  }

  private def getMessages(flowName: String) = withExistingFlow(flowName) { name =>
    forwardToFlow(name)(Flow.GetMessages)
  }

  private def addMessage(flowName: String, text: String) = withExistingFlow(flowName) { name =>
    forwardToFlow(name)(Flow.AddMessage(text))
  }

  private def withUnknownFlow(label: String)(f: String => Unit) = {
    val name = labelToName(label)
    if (!flowsByName.contains(name)) f(name) else sender() ! FlowExists(label)
  }

  private def withExistingFlow(name: String)(f: String => Unit) =
    if (flowsByName.contains(name)) f(name) else sender() ! FlowUnknown(name)
}
