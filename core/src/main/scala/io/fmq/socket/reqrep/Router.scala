package io.fmq.socket.reqrep

import cats.effect.kernel.Sync
import io.fmq.address.Uri
import io.fmq.options.{RouterHandover, RouterMandatory}
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SendOptions, SocketFactory, SocketOptions}
import io.fmq.socket.{BidirectionalSocket, Connectivity}
import org.zeromq.ZMQ

final class Router[F[_]: Sync] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket
) extends Connectivity.All[F, Router.Socket]
    with SocketOptions[F]
    with CommonOptions.All[F]
    with SendOptions.All[F]
    with ReceiveOptions.All[F]
    with RouterOptions.Set[F]
    with RequestReplyOptions.All[F]

object Router {

  final class Socket[F[_]: Sync] private[Router] (
      protected[fmq] val socket: ZMQ.Socket,
      val uri: Uri.Complete
  ) extends BidirectionalSocket[F]
      with RouterOptions.Set[F]
      with RequestReplyOptions.All[F]

  implicit val routerSocketFactory: SocketFactory[Router.Socket] = new SocketFactory[Router.Socket] {

    override def create[F[_]: Sync](socket: ZMQ.Socket, uri: Uri.Complete): Router.Socket[F] =
      new Router.Socket[F](socket, uri)

  }

}

private[reqrep] object RouterOptions {

  private[reqrep] trait Set[F[_]] {
    self: SocketOptions[F] =>

    def setMandatory(mandatory: RouterMandatory): F[Unit] =
      F.void(F.delay(socket.setRouterMandatory(mandatory.value)))

    def setHandover(handover: RouterHandover): F[Unit] =
      F.void(F.delay(socket.setRouterHandover(handover.value)))

  }

}
