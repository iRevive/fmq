package io.fmq.socket.reqrep

import cats.effect.{Sync}
import io.fmq.address.Uri
import io.fmq.socket.api.{CommonOptions, SendOptions, SocketFactory, SocketOptions}
import io.fmq.socket.{BidirectionalSocket, Connectivity}
import org.zeromq.ZMQ

final class Reply[F[_]: Sync] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket
) extends Connectivity.All[F, Reply.Socket]
    with SocketOptions[F]
    with CommonOptions.All[F]
    with SendOptions.All[F]
    with RequestReplyOptions.All[F]

object Reply {

  final class Socket[F[_]: Sync] private[Reply] (
      protected[fmq] val socket: ZMQ.Socket,
      val uri: Uri.Complete
  ) extends BidirectionalSocket[F]
      with RequestReplyOptions.All[F]

  implicit val replySocketFactory: SocketFactory[Reply.Socket] = new SocketFactory[Reply.Socket] {

    override def create[F[_]: Sync](socket: ZMQ.Socket, uri: Uri.Complete): Reply.Socket[F] =
      new Reply.Socket[F](socket, uri)

  }

}
