package io.fmq.options

sealed abstract class SocketType(val zmqType: org.zeromq.SocketType)

object SocketType {

  final case object Pull extends SocketType(org.zeromq.SocketType.PULL)
  final case object Push extends SocketType(org.zeromq.SocketType.PUSH)
  final case object Pub  extends SocketType(org.zeromq.SocketType.PUB)
  final case object Sub  extends SocketType(org.zeromq.SocketType.SUB)

}
