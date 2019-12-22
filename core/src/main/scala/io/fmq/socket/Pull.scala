package io.fmq.socket

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.domain.Protocol.tcp
import io.fmq.socket.api.SendOptions
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class Pull[F[_]: ContextShift] private[fmq] (
    protected val socket: ZMQ.Socket,
    blocker: Blocker
)(implicit protected val F: Sync[F])
    extends SendOptions[F] {

  def bind(protocol: tcp.HostPort): Resource[F, ConsumerSocket[F]] =
    Bind.bind[F](protocol, socket, blocker).as(new ConsumerSocket(socket, protocol.port))

  def bindToRandomPort(protocol: tcp.Host): Resource[F, ConsumerSocket[F]] =
    for {
      port <- Bind.bindToRandomPort[F](protocol, socket, blocker)
    } yield new ConsumerSocket(socket, port)

}
