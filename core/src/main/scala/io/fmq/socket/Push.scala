package io.fmq.socket

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.domain.Protocol.tcp
import io.fmq.socket.api.{CommonOptions, SendOptions, SocketOptions}
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class Push[F[_]: Sync: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    blocker: Blocker
) extends SocketOptions[F]
    with CommonOptions.All[F]
    with SendOptions.All[F] {

  override protected def F: Sync[F] = implicitly[Sync[F]]

  def connect(protocol: tcp.HostPort): Resource[F, ProducerSocket[F]] =
    Bind.connect[F](protocol, socket, blocker).as(new ProducerSocket[F](socket, protocol.port))

}
