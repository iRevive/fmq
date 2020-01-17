package io.fmq.socket.internal

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.domain.Protocol.tcp
import io.fmq.domain.{Port, Protocol}
import org.zeromq.ZMQ

private[socket] object Bind {

  def connect[F[_]: Sync: ContextShift](protocol: tcp.HostPort, socket: ZMQ.Socket, blocker: Blocker): Resource[F, Unit] = {
    val address = Protocol.materialize(protocol)

    val acquire: F[ZMQ.Socket]          = blocker.delay(socket.connect(Protocol.materialize(protocol))).as(socket)
    def release(s: ZMQ.Socket): F[Unit] = blocker.delay(s.disconnect(address)).void

    Resource.make(acquire)(release).void
  }

  def bind[F[_]: Sync: ContextShift](protocol: tcp.HostPort, socket: ZMQ.Socket, blocker: Blocker): Resource[F, Unit] = {
    val address = Protocol.materialize(protocol)

    val acquire: F[ZMQ.Socket]          = blocker.delay(socket.bind(Protocol.materialize(protocol))).as(socket)
    def release(s: ZMQ.Socket): F[Unit] = blocker.delay(s.unbind(address)).void

    Resource.make(acquire)(release).void
  }

  def bindToRandomPort[F[_]: Sync: ContextShift](protocol: tcp.Host, socket: ZMQ.Socket, blocker: Blocker): Resource[F, Port] = {
    val acquire: F[Port]             = blocker.delay(Port(socket.bindToRandomPort(Protocol.materialize(protocol))))
    def release(port: Port): F[Unit] = blocker.delay(socket.unbind(Protocol.materialize(tcp.HostPort(protocol.host, port)))).void

    Resource.make(acquire)(release)
  }

}
