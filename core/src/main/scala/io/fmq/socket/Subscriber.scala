package io.fmq
package socket

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.domain.Protocol.tcp
import io.fmq.domain.SubscribeTopic
import io.fmq.socket.api.ReceiveOptions
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class Subscriber[F[_]: ContextShift, H[_]: Sync] private[fmq] (
    val topic: SubscribeTopic,
    protected val socket: ZMQ.Socket,
    blocker: Blocker
)(implicit protected val F: Sync[F])
    extends ReceiveOptions[F] {

  def connect(protocol: tcp.HostPort): Resource[F, ConsumerSocket[H]] =
    Bind.connect[F](protocol, socket, blocker).as(new ConsumerSocket[H](socket, protocol.port))

}
