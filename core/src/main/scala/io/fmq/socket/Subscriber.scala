package io.fmq.socket

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.domain.Protocol.tcp
import io.fmq.domain.SubscribeTopic
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SocketOptions}
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class Subscriber[F[_]: Sync: ContextShift] private[fmq] (
    val topic: SubscribeTopic,
    protected[fmq] val socket: ZMQ.Socket,
    blocker: Blocker
) extends SocketOptions[F]
    with CommonOptions.All[F]
    with ReceiveOptions.All[F] {

  override protected def F: Sync[F] = implicitly[Sync[F]]

  def connect(protocol: tcp.HostPort): Resource[F, ConsumerSocket[F]] =
    Bind.connect[F](protocol, socket, blocker).as(new ConsumerSocket[F](socket, protocol.port))

}
