package io.fmq.socket.api

import cats.effect.{Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.Uri
import org.zeromq.ZMQ

trait ConnectApi[F[_], Socket[_[_]]] {

  protected implicit val F: Sync[F]
  protected implicit val SF: SocketFactory[Socket]

  protected[fmq] def socket: ZMQ.Socket

  final def connect(uri: Uri.Complete): Resource[F, Socket[F]] = {
    val address = uri.materialize

    val acquire: F[ZMQ.Socket]          = F.blocking(socket.connect(address)).as(socket)
    def release(s: ZMQ.Socket): F[Unit] = F.blocking(s.disconnect(address)).void

    Resource.make(acquire)(release).as(SF.create[F](socket, uri))
  }

}
