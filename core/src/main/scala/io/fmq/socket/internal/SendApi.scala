package io.fmq.socket.internal

import java.nio.charset.StandardCharsets

private[socket] trait SendApi[F[_]] {

  protected def socket: Socket[F]

  def send(bytes: Array[Byte]): F[Unit]     = socket.send(bytes)
  def sendMore(bytes: Array[Byte]): F[Unit] = socket.sendMore(bytes)

  def sendUtf8String(string: String): F[Unit]     = send(string.getBytes(StandardCharsets.UTF_8))
  def sendUtf8StringMore(string: String): F[Unit] = sendMore(string.getBytes(StandardCharsets.UTF_8))

}
