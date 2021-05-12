package io.fmq.socket.api

import cats.effect.kernel.Sync
import io.fmq.address.Uri
import org.zeromq.ZMQ

trait SocketFactory[Socket[_[_]]] {
  def create[F[_]: Sync](socket: ZMQ.Socket, uri: Uri.Complete): Socket[F]
}

object SocketFactory {
  def apply[Socket[_[_]]](implicit instance: SocketFactory[Socket]): SocketFactory[Socket] = instance
}
