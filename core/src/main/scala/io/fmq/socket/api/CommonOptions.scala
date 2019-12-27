package io.fmq.socket.api

import io.fmq.domain.{Identity, Linger}

object CommonOptions {

  private[socket] trait All[F[_]] extends Get[F] with Set[F] {
    self: SocketOptions[F] =>
  }

  private[socket] trait Get[F[_]] {
    self: SocketOptions[F] =>

    def identity: F[Identity] = F.delay(Identity(socket.getIdentity))
    def linger: F[Linger]     = F.delay(Linger.fromInt(socket.getLinger))
  }

  private[socket] trait Set[F[_]] {
    self: SocketOptions[F] =>

    def setIdentity(identity: Identity): F[Unit] = F.void(F.delay(socket.setIdentity(identity.value)))
    def setLinger(linger: Linger): F[Unit]       = F.void(F.delay(socket.setLinger(linger.value)))
  }

}
