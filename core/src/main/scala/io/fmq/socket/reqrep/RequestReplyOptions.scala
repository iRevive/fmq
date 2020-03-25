package io.fmq.socket.reqrep

import io.fmq.options.Identity
import io.fmq.socket.api.SocketOptions

private[reqrep] object RequestReplyOptions {

  private[socket] trait All[F[_]] extends Get[F] with Set[F] { self: SocketOptions[F] => }

  private[socket] trait Get[F[_]] {
    self: SocketOptions[F] =>

    def identity: F[Identity] = F.delay(Identity(socket.getIdentity))
  }

  private[socket] trait Set[F[_]] {
    self: SocketOptions[F] =>

    def setIdentity(identity: Identity): F[Unit] = F.void(F.delay(socket.setIdentity(identity.value)))
  }

}
