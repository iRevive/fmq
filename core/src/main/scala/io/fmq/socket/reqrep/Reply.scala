package io.fmq.socket.reqrep

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.Uri
import io.fmq.socket.ProducerConsumerSocket
import io.fmq.socket.api.{CommonOptions, SendOptions, SocketOptions}
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class Reply[F[_]: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    blocker: Blocker
)(implicit protected val F: Sync[F])
    extends SocketOptions[F]
    with CommonOptions.All[F]
    with SendOptions.All[F] {

  def bind(uri: Uri.Complete): Resource[F, Reply.Socket[F]] =
    Bind.bind[F](uri, socket, blocker).as(new Reply.Socket(socket, uri))

  def bindToRandomPort(uri: Uri.Incomplete.TCP): Resource[F, Reply.Socket[F]] =
    for {
      completeUri <- Bind.bindToRandomPort[F](uri, socket, blocker)
    } yield new Reply.Socket(socket, completeUri)

}

object Reply {

  final class Socket[F[_]: Sync] private[Reply] (
      socket: ZMQ.Socket,
      uri: Uri.Complete
  ) extends ProducerConsumerSocket[F](socket, uri)

}
