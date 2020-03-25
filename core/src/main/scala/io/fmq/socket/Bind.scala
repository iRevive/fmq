package io.fmq.socket

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.{Address, Port, Uri}
import org.zeromq.ZMQ

abstract class Bind[F[_]: ContextShift, Socket[_[_]]: SocketFactory](implicit protected val F: Sync[F]) {

  protected[fmq] def socket: ZMQ.Socket
  protected def blocker: Blocker

  final def bind(uri: Uri.Complete): Resource[F, Socket[F]] = {
    val address = uri.materialize

    val acquire: F[ZMQ.Socket]          = blocker.delay(socket.bind(address)).as(socket)
    def release(s: ZMQ.Socket): F[Unit] = blocker.delay(s.unbind(address)).void

    Resource.make(acquire)(release).as(SocketFactory[Socket].create[F](socket, uri))
  }

  final def bindToRandomPort(uri: Uri.Incomplete.TCP): Resource[F, Socket[F]] = {
    val acquire: F[Uri.Complete.TCP] = blocker.delay {
      val port = socket.bindToRandomPort(uri.materialize)
      Uri.Complete.TCP(Address.Full(uri.address.host, Port(port)))
    }

    def release(uri: Uri.Complete.TCP): F[Unit] =
      blocker.delay(socket.unbind(uri.materialize)).void

    for {
      completeUri <- Resource.make(acquire)(release)
    } yield SocketFactory[Socket].create[F](socket, completeUri)
  }

}

abstract class Connect[F[_]: ContextShift, Socket[_[_]]: SocketFactory](implicit protected val F: Sync[F]) {

  protected[fmq] def socket: ZMQ.Socket
  protected def blocker: Blocker

  final def connect(uri: Uri.Complete): Resource[F, Socket[F]] = {
    val address = uri.materialize

    val acquire: F[ZMQ.Socket]          = blocker.delay(socket.connect(address)).as(socket)
    def release(s: ZMQ.Socket): F[Unit] = blocker.delay(s.disconnect(address)).void

    Resource.make(acquire)(release).as(SocketFactory[Socket].create[F](socket, uri))
  }

}

trait SocketFactory[Socket[_[_]]] {
  def create[F[_]: Sync](socket: ZMQ.Socket, uri: Uri.Complete): Socket[F]
}

object SocketFactory {
  def apply[Socket[_[_]]](implicit instance: SocketFactory[Socket]): SocketFactory[Socket] = instance
}
