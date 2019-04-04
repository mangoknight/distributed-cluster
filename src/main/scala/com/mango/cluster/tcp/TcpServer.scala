package com.mango.cluster.tcp

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.sharding.ClusterSharding
import akka.io.{IO, Tcp}
import akka.io.Tcp.{Received, Write}
import akka.util.ByteString
import com.mango.cluster.entities.Entity
import org.apache.gearpump.util.HostPort


object TcpServer {
  def props( confHostPort: HostPort): Props =
    Props(classOf[TcpServer],  confHostPort)
  case class WriteTcp(str:String) //extends Serializable
  val map =  collection.mutable.Map.empty[Int,String]

}
class TcpServer( confHostPort: HostPort) extends Actor with ActorLogging {

  import TcpServer._

  var connection: ActorRef = null
  // val shardRegion:ActorSelection

  import context.system
  //The manager receives I/O command messages and instantiates worker actors in response.
  IO(Tcp) ! Tcp.Bind(self, new InetSocketAddress(confHostPort.host, confHostPort.port))


  override def receive = {
    case Tcp.CommandFailed(_: Tcp.Bind) => context stop self
    case b@Tcp.Bound(localAddress) ⇒ //The actor sending the Bind message will receive a Bound message signaling that the server is ready to accept incoming connections
      log.info(s"Started listen on : {}", localAddress.getPort)
    case Tcp.Connected(remote, local) =>
      connection = sender()
      connection ! Tcp.Register(self)
    case Received(s) =>
      val shardRegion = ClusterSharding(system).shardRegion("Entity")
      shardRegion ! Entity.Hello(s.utf8String.trim.toInt,"")
    case w:WriteTcp => connection ! Write(ByteString(w.str))  //tcp out
  }
}

