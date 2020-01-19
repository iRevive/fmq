package io.fmq.socket.api

import io.fmq.options.SendTimeout

object SendOptions {

  private[socket] trait All[F[_]] extends Get[F] with Set[F] {
    self: SocketOptions[F] =>
  }

  private[socket] trait Get[F[_]] {
    self: SocketOptions[F] =>

    def sendTimeout: F[SendTimeout] = F.delay(SendTimeout.fromInt(socket.getSendTimeOut))
  }

  private[socket] trait Set[F[_]] {
    self: SocketOptions[F] =>

    def setSendTimeout(timeout: SendTimeout): F[Unit] = F.void(F.delay(socket.setSendTimeOut(timeout.value)))
  }

}
