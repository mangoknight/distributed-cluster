package com.mango.cluster.entities

import akka.actor.Actor
import akka.cluster.sharding.ShardRegion
import com.mango.cluster.entities.Entity.Hello

object Entity{
  val extractShardId: ShardRegion.ExtractShardId = {
    case ShardRegion.StartEntity(id) ⇒
      (id.toInt % maxNumberOfShards).toString

    case x => (extractEntityId(x)._1.toInt % maxNumberOfShards).toString
  }
  val maxNumberOfShards = 10
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg @ Hello(entityId,_) => (entityId.toString,msg)
  }
  final case class Hello(entityId: Int,content: String)
}
class Entity extends Actor{
  override def receive: Receive = {
    case Hello(id,content) =>
      sender() ! self.path.address.toString + "  " +self.path.name
  }
}
