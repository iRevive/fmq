package io.fmq.socket

import java.nio.charset.StandardCharsets

import cats.effect.Sync
import cats.syntax.functor._
import io.fmq.domain.Port
import io.fmq.socket.api.SendOptions
import org.zeromq.ZMQ

final class ProducerSocket[F[_]](
    protected val socket: ZMQ.Socket,
    val port: Port
)(implicit protected val F: Sync[F])
    extends SendOptions[F] {

  def send(bytes: Array[Byte]): F[Unit]     = F.delay(socket.send(bytes)).void
  def sendMore(bytes: Array[Byte]): F[Unit] = F.delay(socket.sendMore(bytes)).void

  def sendUtf8String(string: String): F[Unit]     = send(string.getBytes(StandardCharsets.UTF_8))
  def sendUtf8StringMore(string: String): F[Unit] = sendMore(string.getBytes(StandardCharsets.UTF_8))

}
