package io.fmq.domain

sealed abstract class Protocol(val name: String)

object Protocol {

  final case object TCP extends Protocol("tcp")

}
