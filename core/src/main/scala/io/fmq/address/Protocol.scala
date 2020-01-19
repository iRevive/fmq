package io.fmq.address

sealed abstract class Protocol(val name: String)

object Protocol {

  type TCP    = TCP.type
  type InProc = InProc.type

  final case object TCP    extends Protocol("tcp")
  final case object InProc extends Protocol("inproc")

}
