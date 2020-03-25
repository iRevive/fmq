package io.fmq.socket.pubsub

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.Uri
import io.fmq.socket.ProducerSocket
import io.fmq.socket.api.{CommonOptions, SendOptions, SocketOptions}
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class Publisher[F[_]: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    blocker: Blocker
)(implicit protected val F: Sync[F])
    extends SocketOptions[F]
    with CommonOptions.All[F]
    with SendOptions.All[F] {

  def bind(uri: Uri.Complete): Resource[F, Publisher.Socket[F]] =
    Bind.bind[F](uri, socket, blocker).as(new Publisher.Socket(socket, uri))

  def bindToRandomPort(uri: Uri.Incomplete.TCP): Resource[F, Publisher.Socket[F]] =
    for {
      completeUri <- Bind.bindToRandomPort[F](uri, socket, blocker)
    } yield new Publisher.Socket(socket, completeUri)

}

object Publisher {

  final class Socket[F[_]: Sync] private[Publisher] (
      socket: ZMQ.Socket,
      uri: Uri.Complete
  ) extends ProducerSocket.Connected[F](socket, uri)

}
