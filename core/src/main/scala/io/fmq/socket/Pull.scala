package io.fmq.socket

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.{Address, IsComplete, Protocol, Uri}
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SocketOptions}
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class Pull[F[_]: Sync: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    blocker: Blocker
) extends SocketOptions[F]
    with CommonOptions.All[F]
    with ReceiveOptions.All[F] {

  override protected def F: Sync[F] = implicitly[Sync[F]]

  def bind[P <: Protocol, A <: Address: IsComplete[P, *]](uri: Uri[P, A]): Resource[F, ConsumerSocket[F, P, A]] =
    Bind.bind[F, P, A](uri, socket, blocker).as(new ConsumerSocket(socket, uri))

  def bindToRandomPort(uri: Uri.TCP[Address.HostOnly]): Resource[F, ConsumerSocket[F, Protocol.TCP, Address.Full]] =
    for {
      uriFull <- Bind.bindToRandomPort[F](uri, socket, blocker)
    } yield new ConsumerSocket(socket, uriFull)

}
