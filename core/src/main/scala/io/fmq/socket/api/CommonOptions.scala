package io.fmq.socket.api

import io.fmq.options.Linger

object CommonOptions {

  private[socket] trait All[F[_]] extends Get[F] with Set[F] { self: SocketOptions[F] => }

  private[socket] trait Get[F[_]] {
    self: SocketOptions[F] =>

    def linger: F[Linger] = F.delay(Linger.fromInt(socket.getLinger))
  }

  private[socket] trait Set[F[_]] {
    self: SocketOptions[F] =>

    def setLinger(linger: Linger): F[Unit] = F.void(F.delay(socket.setLinger(linger.value)))
  }

}
