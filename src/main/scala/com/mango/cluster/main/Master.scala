package com.mango.cluster.main

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.mango.cluster.cluster.ClusterWatcher
import com.mango.cluster.ddata.InMemoryKVService
import com.mango.cluster.entities.Entity
import com.mango.cluster.tcp.TcpServer
import com.typesafe.config.{Config, ConfigValueFactory}
import org.apache.gearpump.cluster.ClusterConfig
import org.apache.gearpump.util.Constants._
import org.apache.gearpump.util.LogUtil.ProcessType
import org.apache.gearpump.util.{Constants, HostPort, LogUtil}
import org.slf4j.Logger

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.collection.JavaConverters._

object Master{
  private var LOG: Logger = LogUtil.getLogger(getClass)
  def akkaConf: Config = ClusterConfig.master()
  def main(args: Array[String]): Unit = {
    this.LOG = {
      LogUtil.loadConfiguration(akkaConf, ProcessType.MASTER)
      LogUtil.getLogger(getClass)
    }
    val ipconfig = parse(args)
    master(ipconfig("-ip"), ipconfig("-port").toInt, akkaConf)
  }
  //解析参数 -ip xxx.xxx.xxx.xxx -port xxxx
  def parse(arg: Array[String]): Map[String,String] ={
    var config: Map[String,String] = Map()
    for(x <- 0 to arg.length-1){
      if(arg(x).startsWith("-"))
        config += (arg(x) -> arg(x+1))
    }
    config
  }
  private def verifyMaster(master: String, port: Int, masters: Iterable[String]) = {
    masters.exists { hostPort =>
      hostPort == s"$master:$port"
    }
  }
  private def master(ip: String, port: Int, akkaConf: Config): Unit = {

    val masters = akkaConf.getStringList(Constants.CLUSTER_MASTERS).asScala

    if (!verifyMaster(ip, port, masters)) {
      LOG.error(s"The provided ip $ip and port $port doesn't conform with config at " +
        s"ds.cluster.masters: ${masters.mkString(", ")}")
      System.exit(-1)
    }
    val masterList = masters.map(master => s"akka.tcp://ds@$master").toList.asJava
    val quorum = 1
    val masterConfig = akkaConf.
      withValue("akka.remote.netty.tcp.port", ConfigValueFactory.fromAnyRef(port)).
      withValue(NETTY_TCP_HOSTNAME, ConfigValueFactory.fromAnyRef(ip)).
      withValue("akka.cluster.seed-nodes", ConfigValueFactory.fromAnyRef(masterList))

    LOG.info(s"Starting Master Actor system $ip:$port, master list: ${masters.mkString(";")}")
    val system = ActorSystem("ds", masterConfig)


    //    val ref = system.actorOf(Props[SharedLeveldbStore], "store")
    //    SharedLeveldbJournal.setStore(ref, system)

    // Starts singleton manager
    val singletonManager = system.actorOf(ClusterSingletonManager.props(
      singletonProps = Props(classOf[ClusterWatcher]),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system).withSingletonName(CLUSTER_WATCHER)
        .withRole(MASTER)),
      name = SINGLETON_MANAGER
    )
    //INFO ClusterSingletonManager: Singleton manager starting singleton actor [akka://master/user/singleton/clusterwatcher]

    // Start master proxy
    val masterProxy = system.actorOf(ClusterSingletonProxy.props(
      singletonManagerPath = s"/user/${SINGLETON_MANAGER}",
      // The effective singleton is s"${MASTER_WATCHER}/$MASTER" instead of s"${MASTER_WATCHER}".
      // Master is created when there is a majority of machines started.
      settings = ClusterSingletonProxySettings(system)
        .withSingletonName(s"${CLUSTER_WATCHER}").withRole(MASTER)),
      name = SINGLETON_PROXY
    )
    val kvService = system.actorOf(Props(new InMemoryKVService()), "kvService")



    // 创建分片
    val shardRegion = ClusterSharding(system).start(
      typeName = "Entity",
      entityProps = Props(classOf[Entity]),
      settings = ClusterShardingSettings(system),
      extractEntityId = Entity.extractEntityId,
      extractShardId = Entity.extractShardId
    )

    val tcpPort = akkaConf.getInt("tcpServer.listenPort")
    LOG.info(s"Starting listen on $tcpPort")
    val tcpServer = system.actorOf(
      TcpServer.props( HostPort(ip,tcpPort)),
      "TcpServer")


    val mainThread = Thread.currentThread()
    Runtime.getRuntime().addShutdownHook(new Thread() {
      override def run(): Unit = {
        if (!system.whenTerminated.isCompleted) {
          LOG.info("Triggering shutdown hook....")

          val cluster = Cluster(system)
          cluster.leave(cluster.selfAddress)
          cluster.down(cluster.selfAddress)
          try {
            Await.result(system.whenTerminated, Duration(3, TimeUnit.SECONDS))
          } catch {
            case ex: Exception => // Ignore
          }
          system.terminate()
          mainThread.join()
        }
      }
    })
    Await.result(system.whenTerminated, Duration.Inf)
  }
}
