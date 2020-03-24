package io.fmq.socket.api

import cats.effect.Sync
import org.zeromq.ZMQ

private[socket] trait SocketOptions[F[_]] {
  protected implicit def F: Sync[F]
  private[fmq] def socket: ZMQ.Socket
}
