package io.fmq.socket.pubsub

import cats.effect.{Blocker, ContextShift, Sync}
import io.fmq.address.Uri
import io.fmq.socket.api.{CommonOptions, SendOptions, SocketOptions}
import io.fmq.socket.{Bind, ProducerSocket, SocketFactory}
import org.zeromq.ZMQ

final class Publisher[F[_]: Sync: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    protected val blocker: Blocker
) extends Bind[F, Publisher.Socket]
    with SocketOptions[F]
    with CommonOptions.All[F]
    with SendOptions.All[F]

object Publisher {

  final class Socket[F[_]: Sync] private[Publisher] (
      protected[fmq] val socket: ZMQ.Socket,
      val uri: Uri.Complete
  ) extends ProducerSocket.Connected[F]

  implicit val publisherSocketFactory: SocketFactory[Publisher.Socket] = new SocketFactory[Publisher.Socket] {

    override def create[F[_]: Sync](socket: ZMQ.Socket, uri: Uri.Complete): Publisher.Socket[F] =
      new Publisher.Socket[F](socket, uri)
  }

}
