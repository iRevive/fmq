package io.fmq.socket.api

import cats.effect.Sync
import io.fmq.domain.{Identity, Linger}
import org.zeromq.ZMQ

trait CommonOptions[F[_]] {

  protected def F: Sync[F]
  private[fmq] def socket: ZMQ.Socket

  def identity: F[Identity]                    = F.delay(Identity(socket.getIdentity))
  def setIdentity(identity: Identity): F[Unit] = F.void(F.delay(socket.setIdentity(identity.value)))

  def linger: F[Linger]                  = F.delay(Linger.fromInt(socket.getLinger))
  def setLinger(linger: Linger): F[Unit] = F.void(F.delay(socket.setLinger(linger.value)))

}
