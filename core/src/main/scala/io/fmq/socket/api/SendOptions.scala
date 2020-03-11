package io.fmq.socket.api

import io.fmq.options.{HighWaterMark, SendTimeout}

object SendOptions {

  private[socket] trait All[F[_]] extends Get[F] with Set[F] { self: SocketOptions[F] => }

  private[socket] trait Get[F[_]] {
    self: SocketOptions[F] =>

    def sendTimeout: F[SendTimeout]         = F.delay(SendTimeout.fromInt(socket.getSendTimeOut))
    def sendHighWaterMark: F[HighWaterMark] = F.delay(HighWaterMark.fromInt(socket.getSndHWM))
  }

  private[socket] trait Set[F[_]] {
    self: SocketOptions[F] =>

    def setSendTimeout(timeout: SendTimeout): F[Unit]     = F.void(F.delay(socket.setSendTimeOut(timeout.value)))
    def setSendHighWaterMark(hwm: HighWaterMark): F[Unit] = F.void(F.delay(socket.setSndHWM(hwm.value)))
  }

}
