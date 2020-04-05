package io.fmq.socket.api

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.Uri
import org.zeromq.ZMQ

trait ConnectApi[F[_], Socket[_[_]]] {

  protected implicit val CS: ContextShift[F]
  protected implicit val F: Sync[F]
  protected implicit val SF: SocketFactory[Socket]

  protected[fmq] def socket: ZMQ.Socket
  protected def blocker: Blocker

  final def connect(uri: Uri.Complete): Resource[F, Socket[F]] = {
    val address = uri.materialize

    val acquire: F[ZMQ.Socket]          = blocker.delay(socket.connect(address)).as(socket)
    def release(s: ZMQ.Socket): F[Unit] = blocker.delay(s.disconnect(address)).void

    Resource.make(acquire)(release).as(SF.create[F](socket, uri))
  }

}
