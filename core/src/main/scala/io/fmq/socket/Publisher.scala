package io.fmq
package socket

import cats.Applicative
import cats.effect.Resource
import io.fmq.domain.{Address, Port}
import io.fmq.socket.internal.{ConnectedSocket, SendApi, Socket, SocketApi}

final class Publisher[F[_]: Applicative] private[fmq] (val socket: Socket[F]) extends SocketApi[F] {

  def bind(address: Address): Resource[F, Publisher.Connected[F]] =
    for {
      connected <- socket.bind(address)
    } yield new Publisher.Connected(connected)

}

object Publisher {

  final class Connected[F[_]](connectedSocket: ConnectedSocket[F]) extends SocketApi[F] with SendApi[F] {
    override protected def socket: Socket[F] = connectedSocket.socket
    def port: Port                           = connectedSocket.port
  }

}
