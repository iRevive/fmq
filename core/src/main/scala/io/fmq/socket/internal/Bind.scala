package io.fmq.socket.internal

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.domain.Protocol.tcp
import io.fmq.domain.{Port, Protocol}
import org.zeromq.ZMQ

private[socket] object Bind {

  def connect[F[_]: Sync: ContextShift](protocol: tcp.HostPort, socket: ZMQ.Socket, blocker: Blocker): Resource[F, Unit] =
    Resource.fromAutoCloseable(blocker.delay(socket.connect(Protocol.materialize(protocol))).as(socket)).void

  def bind[F[_]: Sync: ContextShift](protocol: tcp.HostPort, socket: ZMQ.Socket, blocker: Blocker): Resource[F, Unit] =
    Resource.fromAutoCloseable(blocker.delay(socket.bind(Protocol.materialize(protocol))).as(socket)).void

  def bindToRandomPort[F[_]: Sync: ContextShift](protocol: tcp.Host, socket: ZMQ.Socket, blocker: Blocker): Resource[F, Port] = {
    val acquire: F[Port] = blocker.delay(Port(socket.bindToRandomPort(Protocol.materialize(protocol))))
    val release: F[Unit] = blocker.delay(socket.close())

    Resource.make(acquire)(_ => release)
  }

}
