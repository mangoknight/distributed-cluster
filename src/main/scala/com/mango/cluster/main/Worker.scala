package com.mango.cluster.main

import akka.actor.{ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import com.mango.cluster.ddata.InMemoryKVService
import com.mango.cluster.entities.Entity
import com.typesafe.config.{Config, ConfigValueFactory}
import org.apache.gearpump.cluster.ClusterConfig
import org.apache.gearpump.util.Constants.NETTY_TCP_HOSTNAME
import org.apache.gearpump.util.LogUtil
import org.apache.gearpump.util.LogUtil.ProcessType
import org.slf4j.Logger

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Worker {
  def akkaConf: Config = ClusterConfig.worker()

  var LOG: Logger = LogUtil.getLogger(getClass)

  private def uuid = java.util.UUID.randomUUID.toString

  def main(args: Array[String]): Unit = {
    val id = uuid
    this.LOG = {
      LogUtil.loadConfiguration(akkaConf, ProcessType.WORKER)
      LogUtil.getLogger(getClass)
    }
    val ipconfig = parse(args)

    val workerConfig = akkaConf.
      withValue("akka.remote.netty.tcp.port", ConfigValueFactory.fromAnyRef(ipconfig("-port"))).
      withValue(NETTY_TCP_HOSTNAME, ConfigValueFactory.fromAnyRef(ipconfig("-ip")))

    val system = ActorSystem("rs", workerConfig)

    val kvService = system.actorOf(Props(new InMemoryKVService()), "kvService")
    // 创建分片
    val shardRegion = ClusterSharding(system).start(
      typeName = "Entity",
      entityProps = Props(classOf[Entity]),
      settings = ClusterShardingSettings(system),
      extractEntityId = Entity.extractEntityId,
      extractShardId = Entity.extractShardId
    )

    Await.result(system.whenTerminated, Duration.Inf)
  }
  def parse(arg: Array[String]): Map[String,String] ={
    var config: Map[String,String] = Map()
    for(x <- 0 to arg.length-1){
      if(arg(x).startsWith("-"))
        config += (arg(x) -> arg(x+1))
    }
    return config
  }
}
