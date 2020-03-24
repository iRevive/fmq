package io.fmq.socket.internal

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.{Address, Port, Uri}
import org.zeromq.ZMQ

private[socket] object Bind {

  def connect[F[_]: Sync: ContextShift](uri: Uri.Complete, socket: ZMQ.Socket, blocker: Blocker): Resource[F, Unit] = {
    val address = uri.materialize

    val acquire: F[ZMQ.Socket]          = blocker.delay(socket.connect(address)).as(socket)
    def release(s: ZMQ.Socket): F[Unit] = blocker.delay(s.disconnect(address)).void

    Resource.make(acquire)(release).void
  }

  def bind[F[_]: Sync: ContextShift](uri: Uri.Complete, socket: ZMQ.Socket, blocker: Blocker): Resource[F, Unit] = {
    val address = uri.materialize

    val acquire: F[ZMQ.Socket]          = blocker.delay(socket.bind(address)).as(socket)
    def release(s: ZMQ.Socket): F[Unit] = blocker.delay(s.unbind(address)).void

    Resource.make(acquire)(release).void
  }

  def bindToRandomPort[F[_]: Sync: ContextShift](
      uri: Uri.Incomplete.TCP,
      socket: ZMQ.Socket,
      blocker: Blocker
  ): Resource[F, Uri.Complete.TCP] = {

    val acquire: F[Uri.Complete.TCP] = blocker.delay {
      val port = socket.bindToRandomPort(uri.materialize)
      Uri.Complete.TCP(Address.Full(uri.address.host, Port(port)))
    }

    def release(uri: Uri.Complete.TCP): F[Unit] =
      blocker.delay(socket.unbind(uri.materialize)).void

    Resource.make(acquire)(release)
  }

}
