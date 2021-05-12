package io.fmq.socket.api

import cats.effect.kernel.{Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.{Address, Uri}
import org.zeromq.ZMQ

trait BindApi[F[_], Socket[_[_]]] {

  protected implicit val F: Sync[F]
  protected implicit val SF: SocketFactory[Socket]

  protected[fmq] def socket: ZMQ.Socket

  final def bind(uri: Uri.Complete): Resource[F, Socket[F]] = {
    val address = uri.materialize

    val acquire: F[ZMQ.Socket]          = F.blocking(socket.bind(address)).as(socket)
    def release(s: ZMQ.Socket): F[Unit] = F.blocking(s.unbind(address)).void

    Resource.make(acquire)(release).as(SF.create[F](socket, uri))
  }

  final def bindToRandomPort(uri: Uri.Incomplete.TCP): Resource[F, Socket[F]] = {
    val acquire: F[Uri.Complete.TCP] = F.blocking {
      val port = socket.bindToRandomPort(uri.materialize)
      Uri.Complete.TCP(Address.Full(uri.address.host, port))
    }

    def release(uri: Uri.Complete.TCP): F[Unit] =
      F.blocking(socket.unbind(uri.materialize)).void

    for {
      completeUri <- Resource.make(acquire)(release)
    } yield SF.create[F](socket, completeUri)
  }

}
