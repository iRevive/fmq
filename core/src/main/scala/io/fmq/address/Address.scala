package io.fmq.address

sealed abstract class Address(val value: String)

object Address {
  final case class HostOnly(host: String)        extends Address(host)
  final case class Full(host: String, port: Int) extends Address(s"$host:${port.toString}")
}
