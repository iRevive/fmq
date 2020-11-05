package io.fmq.socket.pubsub

import cats.effect.{Sync}
import io.fmq.address.Uri
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SendOptions, SocketFactory, SocketOptions}
import io.fmq.socket.{BidirectionalSocket, Connectivity}
import org.zeromq.ZMQ

final class XPublisher[F[_]: Sync] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket
) extends Connectivity.All[F, XPublisher.Socket]
    with SocketOptions[F]
    with CommonOptions.All[F]
    with SendOptions.All[F]
    with ReceiveOptions.All[F]

object XPublisher {

  final class Socket[F[_]: Sync] private[XPublisher] (
      protected[fmq] val socket: ZMQ.Socket,
      val uri: Uri.Complete
  ) extends BidirectionalSocket[F]

  implicit val xPublisherSocketFactory: SocketFactory[XPublisher.Socket] = new SocketFactory[XPublisher.Socket] {

    override def create[F[_]: Sync](socket: ZMQ.Socket, uri: Uri.Complete): XPublisher.Socket[F] =
      new XPublisher.Socket[F](socket, uri)

  }

}
