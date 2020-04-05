package io.fmq.socket.pipeline

import cats.effect.{Blocker, ContextShift, Sync}
import io.fmq.address.Uri
import io.fmq.socket.api.{CommonOptions, SendOptions, SocketFactory, SocketOptions}
import io.fmq.socket.{Connectivity, ProducerSocket}
import org.zeromq.ZMQ

final class Push[F[_]: Sync: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    protected val blocker: Blocker
) extends Connectivity.Connect[F, Push.Socket]
    with SocketOptions[F]
    with CommonOptions.All[F]
    with SendOptions.All[F]

object Push {

  final class Socket[F[_]: Sync] private[Push] (
      protected[fmq] val socket: ZMQ.Socket,
      val uri: Uri.Complete
  ) extends ProducerSocket.Connected[F]

  implicit val pushSocketFactory: SocketFactory[Push.Socket] = new SocketFactory[Push.Socket] {

    override def create[F[_]: Sync](socket: ZMQ.Socket, uri: Uri.Complete): Push.Socket[F] =
      new Push.Socket[F](socket, uri)

  }

}
