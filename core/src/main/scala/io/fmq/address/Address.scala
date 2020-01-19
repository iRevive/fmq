package io.fmq.address

sealed abstract class Address(val value: String)

object Address {
  final case class HostOnly(host: Host)         extends Address(host.value)
  final case class Full(host: Host, port: Port) extends Address(s"${host.value}:${port.value.toString}")
}
