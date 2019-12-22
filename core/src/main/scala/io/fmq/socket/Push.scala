package io.fmq.socket

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.domain.Protocol.tcp
import io.fmq.socket.api.SendOptions
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class Push[F[_]: ContextShift] private[fmq] (
    protected val socket: ZMQ.Socket,
    blocker: Blocker
)(implicit protected val F: Sync[F])
    extends SendOptions[F] {

  def connect(protocol: tcp.HostPort): Resource[F, ProducerSocket[F]] =
    Bind.connect[F](protocol, socket, blocker).as(new ProducerSocket[F](socket, protocol.port))

}
