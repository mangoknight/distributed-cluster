package org.apache.gearpump.util

//package org.apache.gearpump.transport

case class HostPort(host: String, port: Int) {
  def toTuple: (String, Int) = {
    (host, port)
  }
}

object HostPort {
  def apply(address: String): HostPort = {
    val hostAndPort = address.split(":")
    new HostPort(hostAndPort(0), hostAndPort(1).toInt)
  }
}