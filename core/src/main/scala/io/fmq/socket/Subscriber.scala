package io.fmq.socket

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.{Address, IsComplete, Protocol, Uri}
import io.fmq.options.SubscribeTopic
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

  def connect[P <: Protocol, A <: Address: IsComplete[P, *]](uri: Uri[P, A]): Resource[F, ConsumerSocket[F, P, A]] =
    Bind.connect[F, P, A](uri, socket, blocker).as(new ConsumerSocket[F, P, A](socket, uri))

}
