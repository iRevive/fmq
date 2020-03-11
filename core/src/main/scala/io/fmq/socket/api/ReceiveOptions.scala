package io.fmq.socket.api

import io.fmq.options.{HighWaterMark, ReceiveTimeout}

object ReceiveOptions {

  private[socket] trait All[F[_]] extends Get[F] with Set[F] { self: SocketOptions[F] => }

  private[socket] trait Get[F[_]] {
    self: SocketOptions[F] =>

    def receiveTimeout: F[ReceiveTimeout]      = F.delay(ReceiveTimeout.fromInt(socket.getReceiveTimeOut))
    def receiveHighWaterMark: F[HighWaterMark] = F.delay(HighWaterMark.fromInt(socket.getRcvHWM))
  }

  private[socket] trait Set[F[_]] {
    self: SocketOptions[F] =>

    def setReceiveTimeout(timeout: ReceiveTimeout): F[Unit]  = F.void(F.delay(socket.setReceiveTimeOut(timeout.value)))
    def setReceiveHighWaterMark(hwm: HighWaterMark): F[Unit] = F.void(F.delay(socket.setRcvHWM(hwm.value)))
  }

}
