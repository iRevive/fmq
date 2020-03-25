package io.fmq.socket.pubsub

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.Uri
import io.fmq.socket.ProducerConsumerSocket
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SendOptions, SocketOptions}
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class XPublisher[F[_]: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    blocker: Blocker
)(implicit protected val F: Sync[F])
    extends SocketOptions[F]
    with CommonOptions.All[F]
    with SendOptions.All[F]
    with ReceiveOptions.All[F] {

  def bind(uri: Uri.Complete): Resource[F, XPublisher.Socket[F]] =
    Bind.bind[F](uri, socket, blocker).as(new XPublisher.Socket(socket, uri))

  def bindToRandomPort(uri: Uri.Incomplete.TCP): Resource[F, XPublisher.Socket[F]] =
    for {
      completeUri <- Bind.bindToRandomPort[F](uri, socket, blocker)
    } yield new XPublisher.Socket(socket, completeUri)

}

object XPublisher {

  final class Socket[F[_]: Sync] private[XPublisher] (
      socket: ZMQ.Socket,
      uri: Uri.Complete
  ) extends ProducerConsumerSocket[F](socket, uri)

}
