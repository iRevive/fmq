package io.fmq.socket
package pipeline

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.{Address, Complete, Protocol, Uri}
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SocketOptions}
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class Pull[F[_]: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    blocker: Blocker
)(implicit protected val F: Sync[F])
    extends SocketOptions[F]
    with CommonOptions.All[F]
    with ReceiveOptions.All[F] {

  def bind[P <: Protocol, A <: Address: Complete[P, *]](uri: Uri[P, A]): Resource[F, ConsumerSocket[F, P, A]] =
    Bind.bind[F, P, A](uri, socket, blocker).as(ConsumerSocket.create(socket, uri))

  def bindToRandomPort(uri: Uri.TCP[Address.HostOnly]): Resource[F, ConsumerSocket[F, Protocol.TCP, Address.Full]] =
    for {
      uriFull <- Bind.bindToRandomPort[F](uri, socket, blocker)
    } yield ConsumerSocket.create(socket, uriFull)

}
