package io.fmq.socket.api

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.{Address, Uri}
import org.zeromq.ZMQ

trait BindApi[F[_], Socket[_[_]]] {

  protected implicit val CS: ContextShift[F]
  protected implicit val F: Sync[F]
  protected implicit val SF: SocketFactory[Socket]

  protected[fmq] def socket: ZMQ.Socket
  protected def blocker: Blocker

  final def bind(uri: Uri.Complete): Resource[F, Socket[F]] = {
    val address = uri.materialize

    val acquire: F[ZMQ.Socket]          = blocker.delay(socket.bind(address)).as(socket)
    def release(s: ZMQ.Socket): F[Unit] = blocker.delay(s.unbind(address)).void

    Resource.make(acquire)(release).as(SF.create[F](socket, uri))
  }

  final def bindToRandomPort(uri: Uri.Incomplete.TCP): Resource[F, Socket[F]] = {
    val acquire: F[Uri.Complete.TCP] = blocker.delay {
      val port = socket.bindToRandomPort(uri.materialize)
      Uri.Complete.TCP(Address.Full(uri.address.host, port))
    }

    def release(uri: Uri.Complete.TCP): F[Unit] =
      blocker.delay(socket.unbind(uri.materialize)).void

    for {
      completeUri <- Resource.make(acquire)(release)
    } yield SF.create[F](socket, completeUri)
  }

}
