package io.fmq.socket.pipeline

import cats.effect.{Blocker, ContextShift, Sync}
import io.fmq.address.Uri
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SocketOptions}
import io.fmq.socket.{Bind, ConsumerSocket, SocketFactory}
import org.zeromq.ZMQ

final class Pull[F[_]: Sync: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    protected val blocker: Blocker
) extends Bind[F, Pull.Socket]
    with SocketOptions[F]
    with CommonOptions.All[F]
    with ReceiveOptions.All[F]

object Pull {

  final class Socket[F[_]: Sync] private[Pull] (
      protected[fmq] val socket: ZMQ.Socket,
      val uri: Uri.Complete
  ) extends ConsumerSocket.Connected[F]

  implicit val pullSocketFactory: SocketFactory[Pull.Socket] = new SocketFactory[Pull.Socket] {

    override def create[F[_]: Sync](socket: ZMQ.Socket, uri: Uri.Complete): Pull.Socket[F] =
      new Pull.Socket[F](socket, uri)

  }

}
