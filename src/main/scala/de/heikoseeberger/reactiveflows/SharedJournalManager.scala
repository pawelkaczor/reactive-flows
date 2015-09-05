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

import akka.actor.{ Actor, ActorIdentity, ActorLogging, ActorRef, Address, Identify, Props, ReceiveTimeout, RootActorPath, Terminated }
import akka.cluster.Cluster
import akka.persistence.journal.leveldb.{ SharedLeveldbJournal, SharedLeveldbStore }
import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{ Files, Path, Paths, SimpleFileVisitor }
import scala.concurrent.blocking
import scala.concurrent.duration.{ Duration, DurationInt }

object SharedJournalManager {

  final val Name = "shared-journal-manager"

  final val SharedJournal = "shared-journal"

  def props: Props = Props(new SharedJournalManager)

  private def deleteDir(dir: Path): Unit = blocking {
    if (Files.exists(dir)) {
      Files.walkFileTree(dir, new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes) = {
          Files.delete(file)
          super.visitFile(file, attrs)
        }
        override def postVisitDirectory(dir: Path, e: IOException) = {
          Files.delete(dir)
          super.postVisitDirectory(dir, e)
        }
      })
    }
  }
}

class SharedJournalManager extends Actor with ActorLogging {
  import SharedJournalManager._

  Cluster(context.system).state.members.toList.sortWith(_.isOlderThan(_)).headOption.map(_.address) match {
    case Some(address) if address == Cluster(context.system).selfAddress => startSharedJournal()
    case Some(address)                                                   => identifySharedJournal(address)
    case None                                                            => onInvalidClusterState()
  }

  override def receive = {
    case ActorIdentity(_, Some(sharedJournal)) => onSharedJournalIdentified(sharedJournal)
    case ActorIdentity(_, None)                => onSharedJournalNotIdentified()
    case ReceiveTimeout                        => onSharedJournalReceiveTimeout()
  }

  private def watching: Receive = {
    case Terminated(_) => onSharedJournalTerminated()
  }

  private def startSharedJournal() = {
    deleteDir(Paths.get(
      context.system.settings.config.getString("akka.persistence.journal.leveldb-shared.store.dir")
    ))
    val sharedJournal = context.watch(context.actorOf(Props(new SharedLeveldbStore), SharedJournal))
    SharedLeveldbJournal.setStore(sharedJournal, context.system)
    log.debug("Started shared journal {}", sharedJournal)
  }

  private def identifySharedJournal(address: Address) = {
    val sharedJournal = context.actorSelection(RootActorPath(address) / "user" / ReactiveFlows.Name / Name / SharedJournal)
    sharedJournal ! Identify(None)
    context.setReceiveTimeout(10.seconds)
  }

  private def onInvalidClusterState() = {
    log.error("Invalid cluster state: There must at least be one member!")
    context.stop(self)
  }

  private def onSharedJournalIdentified(sharedJournal: ActorRef) = {
    SharedLeveldbJournal.setStore(sharedJournal, context.system)
    log.debug("Succssfully set shared journal {}", sharedJournal)
    context.watch(sharedJournal)
    context.setReceiveTimeout(Duration.Undefined)
    context.become(watching)
  }

  private def onSharedJournalNotIdentified() = {
    log.error("Can't identify shared journal!")
    context.stop(self)
  }

  private def onSharedJournalReceiveTimeout() = {
    log.error("Timeout identifying shared journal!")
    context.stop(self)
  }

  private def onSharedJournalTerminated() = {
    log.error("Shared journal terminated!")
    context.stop(self)
  }
}
