package io.fmq.socket.internal

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.{Address, Port, Protocol, Uri}
import org.zeromq.ZMQ

private[socket] object Bind {

  def connect[F[_]: Sync: ContextShift, P <: Protocol, A <: Address](
      uri: Uri[P, A],
      socket: ZMQ.Socket,
      blocker: Blocker
  ): Resource[F, Unit] = {

    val address = uri.materialize

    val acquire: F[ZMQ.Socket]          = blocker.delay(socket.connect(address)).as(socket)
    def release(s: ZMQ.Socket): F[Unit] = blocker.delay(s.disconnect(address)).void

    Resource.make(acquire)(release).void
  }

  def bind[F[_]: Sync: ContextShift, P <: Protocol, A <: Address](
      uri: Uri[P, A],
      socket: ZMQ.Socket,
      blocker: Blocker
  ): Resource[F, Unit] = {
    val address = uri.materialize

    val acquire: F[ZMQ.Socket]          = blocker.delay(socket.bind(address)).as(socket)
    def release(s: ZMQ.Socket): F[Unit] = blocker.delay(s.unbind(address)).void

    Resource.make(acquire)(release).void
  }

  def bindToRandomPort[F[_]: Sync: ContextShift](
      uri: Uri.TCP[Address.HostOnly],
      socket: ZMQ.Socket,
      blocker: Blocker
  ): Resource[F, Uri.TCP[Address.Full]] = {

    val acquire: F[Uri.TCP[Address.Full]] = blocker.delay {
      val port = socket.bindToRandomPort(uri.materialize)
      uri.copy(address = Address.Full(uri.address.host, Port(port)))
    }

    def release(uri: Uri.TCP[Address.Full]): F[Unit] =
      blocker.delay(socket.unbind(uri.materialize)).void

    Resource.make(acquire)(release)
  }

}
