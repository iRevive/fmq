package io.fmq.socket.reqrep

import cats.effect.{Blocker, ContextShift, Sync}
import io.fmq.address.Uri
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SendOptions, SocketOptions}
import io.fmq.socket.{Bind, ProducerConsumerSocket, SocketFactory}
import org.zeromq.ZMQ

final class Router[F[_]: Sync: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    protected val blocker: Blocker
) extends Bind[F, Router.Socket]
    with SocketOptions[F]
    with CommonOptions.All[F]
    with SendOptions.All[F]
    with ReceiveOptions.All[F]

object Router {

  final class Socket[F[_]: Sync] private[Router] (
      protected[fmq] val socket: ZMQ.Socket,
      val uri: Uri.Complete
  ) extends ProducerConsumerSocket[F]

  implicit val routerSocketFactory: SocketFactory[Router.Socket] = new SocketFactory[Router.Socket] {

    override def create[F[_]: Sync](socket: ZMQ.Socket, uri: Uri.Complete): Router.Socket[F] =
      new Router.Socket[F](socket, uri)
  }

}
