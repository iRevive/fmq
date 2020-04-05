package io.fmq.socket.reqrep

import cats.effect.{Blocker, ContextShift, Sync}
import io.fmq.address.Uri
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SendOptions, SocketFactory, SocketOptions}
import io.fmq.socket.{BidirectionalSocket, Connectivity}
import org.zeromq.ZMQ

final class Dealer[F[_]: Sync: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    protected val blocker: Blocker
) extends Connectivity.All[F, Dealer.Socket]
    with SocketOptions[F]
    with CommonOptions.All[F]
    with SendOptions.All[F]
    with ReceiveOptions.All[F]
    with RequestReplyOptions.All[F]

object Dealer {

  final class Socket[F[_]: Sync] private[Dealer] (
      protected[fmq] val socket: ZMQ.Socket,
      val uri: Uri.Complete
  ) extends BidirectionalSocket[F]
      with RequestReplyOptions.All[F]

  implicit val dealerSocketFactory: SocketFactory[Dealer.Socket] = new SocketFactory[Dealer.Socket] {

    override def create[F[_]: Sync](socket: ZMQ.Socket, uri: Uri.Complete): Dealer.Socket[F] =
      new Dealer.Socket[F](socket, uri)

  }

}
