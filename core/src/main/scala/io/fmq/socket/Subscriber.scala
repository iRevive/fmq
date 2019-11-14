package io.fmq
package socket

import cats.Applicative
import cats.effect.Resource
import io.fmq.domain.{Address, SubscribeTopic}
import io.fmq.socket.internal.{ConnectedSocket, ReceiveApi, Socket, SocketApi}

final class Subscriber[F[_]: Applicative] private[fmq] (
    val topic: SubscribeTopic,
    val socket: Socket[F]
) extends SocketApi[F] {

  def connect(address: Address.Const): Resource[F, Subscriber.Connected[F]] =
    for {
      connected <- socket.connect(address)
    } yield new Subscriber.Connected(connected)

}

object Subscriber {

  final class Connected[F[_]](connectedSocket: ConnectedSocket[F]) extends SocketApi[F] with ReceiveApi[F] {
    override protected def socket: Socket[F] = connectedSocket.socket
  }

}
