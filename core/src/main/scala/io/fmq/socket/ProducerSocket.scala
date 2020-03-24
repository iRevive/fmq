package io.fmq.socket

import cats.effect.Sync
import io.fmq.address.{Address, Complete, Protocol, Uri}
import io.fmq.frame.FrameEncoder
import io.fmq.socket.api.{CommonOptions, SendOptions, SocketOptions}
import org.zeromq.ZMQ

trait ProducerSocket[F[_], P <: Protocol, A <: Address]
    extends ConnectedSocket[P, A]
    with SocketOptions[F]
    with CommonOptions.Get[F]
    with SendOptions.Get[F] {

  def send[B: FrameEncoder](value: B): F[Unit] =
    F.void(F.delay(socket.send(FrameEncoder[B].encode(value))))

  def sendMore[B: FrameEncoder](value: B): F[Unit] =
    F.void(F.delay(socket.sendMore(FrameEncoder[B].encode(value))))

}

object ProducerSocket {

  type TCP[F[_]]    = ProducerSocket[F, Protocol.TCP, Address.Full]
  type InProc[F[_]] = ProducerSocket[F, Protocol.InProc, Address.HostOnly]

  def create[F[_]: Sync, P <: Protocol, A <: Address: Complete[P, *]](s: ZMQ.Socket, u: Uri[P, A]): ProducerSocket[F, P, A] =
    new ConnectedSocket[P, A] with ProducerSocket[F, P, A] {
      override def uri: Uri[P, A] = u

      // $COVERAGE-OFF$
      override protected def complete: Complete[P, A] = implicitly[Complete[P, A]]
      // $COVERAGE-ON$

      override protected def F: Sync[F] = implicitly[Sync[F]]

      override private[fmq] def socket: ZMQ.Socket = s
    }

}
