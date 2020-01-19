package io.fmq.socket

import java.nio.charset.StandardCharsets

import cats.effect.Sync
import cats.syntax.functor._
import io.fmq.address.{Address, IsComplete, Protocol, Uri}
import io.fmq.socket.api.{CommonOptions, SendOptions, SocketOptions}
import org.zeromq.ZMQ

final class ProducerSocket[F[_]: Sync, P <: Protocol, A <: Address: IsComplete[P, *]] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    val uri: Uri[P, A]
) extends SocketOptions[F]
    with CommonOptions.Get[F]
    with SendOptions.Get[F] {

  override protected def F: Sync[F] = implicitly[Sync[F]]

  def send(bytes: Array[Byte]): F[Unit]     = F.delay(socket.send(bytes)).void
  def sendMore(bytes: Array[Byte]): F[Unit] = F.delay(socket.sendMore(bytes)).void

  def sendString(string: String): F[Unit]     = send(string.getBytes(StandardCharsets.UTF_8))
  def sendStringMore(string: String): F[Unit] = sendMore(string.getBytes(StandardCharsets.UTF_8))

}

object ProducerSocket {
  type TCP[F[_]]    = ProducerSocket[F, Protocol.TCP, Address.Full]
  type InProc[F[_]] = ProducerSocket[F, Protocol.InProc, Address.HostOnly]
}
