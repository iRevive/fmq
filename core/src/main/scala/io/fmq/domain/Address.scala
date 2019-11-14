package io.fmq.domain

sealed abstract class Address(val materialize: String)

object Address {

  final case class Const(
      protocol: Protocol,
      host: String,
      port: Port
  ) extends Address(s"${protocol.name}://$host:${port.value.toString}")

  final case class RandomPort(
      protocol: Protocol,
      host: String
  ) extends Address(s"${protocol.name}://$host")

}
