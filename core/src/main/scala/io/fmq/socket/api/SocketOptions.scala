package io.fmq.socket.api

import cats.effect.kernel.Sync
import org.zeromq.ZMQ

private[socket] trait SocketOptions[F[_]] {
  protected implicit def F: Sync[F]
  protected[fmq] def socket: ZMQ.Socket
}
