package com.mango.cluster.cluster

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, Stash}
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, Member, MemberStatus}
import org.apache.gearpump.util.Constants._

import scala.collection.immutable.SortedSet
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ClusterWatcher extends Actor with Stash with ActorLogging {
  import context.dispatcher


  val config = context.system.settings.config
  val quorum = config.getList("akka.cluster.seed-nodes").size() / 2 + 1
  val system = context.system
  val cluster = Cluster(system)

  // Sorts by age, oldest first
  val ageOrdering = Ordering.fromLessThan[Member] { (a, b) => a.isOlderThan(b) }
  var masters = SortedSet.empty[Member](ageOrdering)
  var workers = SortedSet.empty[Member](ageOrdering)

  def receive: Receive = waitForInit //null
  // Subscribes to MemberEvent, re-subscribe when restart
  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent])
    //context.become(waitForInit)
  }
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def matchingRole(member: Member, role: String = MASTER): Boolean = member.hasRole(role)

  def waitForInit: Receive = {
    case state: CurrentClusterState => {
      masters ++= state.members.filter(m =>
        m.status == MemberStatus.Up && matchingRole(m))
      workers ++= state.members.filter(m =>
        m.status == MemberStatus.Up && matchingRole(m, WORKER))
      println("m :"+ masters.size + "w:" + workers.size )
      if (masters.size < quorum) {
        masters.iterator.mkString(",")
        log.info(s"We cannot get a quorum, $quorum, " + s"shutting down...${masters.iterator.mkString(",")}")
        context.become(waitForShutdown)
        self ! ClusterWatcher.Shutdown
      } else {
        context.become(waitForClusterEvent)
        unstashAll()
      }
    }
    case _ => stash()
  }

  def waitForClusterEvent: Receive = {
    case MemberUp(m) if matchingRole(m) => {
      masters += m
      //notifyMasterMembersChange(master)
    }
    case MemberUp(w) if matchingRole(w, WORKER) =>
      workers += w
    case mEvent: MemberEvent if (mEvent.isInstanceOf[MemberExited] || mEvent.isInstanceOf[MemberRemoved]) => {
      if(matchingRole(mEvent.member)) {
        log.info(s"master removed ${mEvent.member}")
        masters -= mEvent.member
        if (masters.size < quorum) {
          log.info(s"We cannot get a quorum, $quorum, " + s"shutting down...${masters.iterator.mkString(",")}")
          context.become(waitForShutdown)
          self ! ClusterWatcher.Shutdown
        } //else {
          //notifyMasterMembersChange(master)
        //}
      } else {
        log.info(s"worker removed ${mEvent.member}")
        workers -= mEvent.member
      }
    }
//    case QueryCluster =>
//      sender() ! Map("master" -> masters.size,"worker" -> workers.size)
//    case QueryShard(s:String) =>
//      val shardRegion=ClusterSharding(system).shardRegion(s)
//      shardRegion ! GetClusterShardingStats
//    case css:ClusterShardingStats =>
//      println(css)
  }

//  private def notifyMasterMembersChange(master: ActorRef): Unit = {
//    val mastersNow = masters.toList.map{ member =>
//      MasterNode(member.address.host.getOrElse("Unknown-Host"),
//        member.address.port.getOrElse(0))
//    }
//    master ! MasterListUpdated(mastersNow)
//  }

  def waitForShutdown: Receive = {
    case ClusterWatcher.Shutdown => {
      cluster.unsubscribe(self)
      cluster.leave(cluster.selfAddress)
      context.stop(self)
      system.scheduler.scheduleOnce(Duration.Zero) {
        try {
          Await.result(system.whenTerminated, Duration(3, TimeUnit.SECONDS))
        } catch {
          case ex: Exception => // Ignore
        }
        system.terminate()
      }
    }
  }
}

object ClusterWatcher {
  object Shutdown
  case object QueryCluster
  case class QueryShard(shardName:String)
}