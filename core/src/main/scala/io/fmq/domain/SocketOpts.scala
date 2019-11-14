package io.fmq.domain

sealed abstract class SocketOpts(val value: Int)

object SocketOpts {

  final case object DontWait extends SocketOpts(1)
  final case object SendMore extends SocketOpts(2)

}
