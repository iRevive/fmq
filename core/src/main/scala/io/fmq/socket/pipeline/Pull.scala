package io.fmq.socket.pipeline

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.Uri
import io.fmq.socket.ConsumerSocket
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

  def bind(uri: Uri.Complete): Resource[F, Pull.Socket[F]] =
    Bind.bind[F](uri, socket, blocker).as(new Pull.Socket(socket, uri))

  def bindToRandomPort(uri: Uri.Incomplete.TCP): Resource[F, Pull.Socket[F]] =
    for {
      completeUri <- Bind.bindToRandomPort[F](uri, socket, blocker)
    } yield new Pull.Socket(socket, completeUri)

}

object Pull {

  final class Socket[F[_]: Sync] private[Pull] (
      socket: ZMQ.Socket,
      uri: Uri.Complete
  ) extends ConsumerSocket.Connected[F](socket, uri)

}
