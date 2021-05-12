package io.fmq.socket.reqrep

import cats.effect.kernel.Sync
import io.fmq.address.Uri
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SocketFactory, SocketOptions}
import io.fmq.socket.{BidirectionalSocket, Connectivity}
import org.zeromq.ZMQ

final class Request[F[_]: Sync] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket
) extends Connectivity.All[F, Request.Socket]
    with SocketOptions[F]
    with CommonOptions.All[F]
    with ReceiveOptions.All[F]
    with RequestReplyOptions.All[F]

object Request {

  final class Socket[F[_]: Sync] private[Request] (
      protected[fmq] val socket: ZMQ.Socket,
      val uri: Uri.Complete
  ) extends BidirectionalSocket[F]
      with RequestReplyOptions.All[F]

  implicit val requestSocketFactory: SocketFactory[Request.Socket] = new SocketFactory[Request.Socket] {

    override def create[F[_]: Sync](socket: ZMQ.Socket, uri: Uri.Complete): Request.Socket[F] =
      new Request.Socket[F](socket, uri)

  }

}
