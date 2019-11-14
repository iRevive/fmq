package io.fmq.socket.internal

import io.fmq.domain.{Identity, Linger, ReceiveTimeout, SendTimeout}

private[fmq] trait SocketApi[F[_]] {

  protected def socket: Socket[F]

  def receiveTimeout: F[ReceiveTimeout] = socket.receiveTimeout
  def sendTimeout: F[SendTimeout]       = socket.sendTimeout
  def linger: F[Linger]                 = socket.linger
  def identity: F[Identity]             = socket.identity

  def setReceiveTimeout(timeout: ReceiveTimeout): F[Unit] = socket.setReceiveTimeout(timeout)
  def setSendTimeout(timeout: SendTimeout): F[Unit]       = socket.setSendTimeout(timeout)
  def setLinger(linger: Linger): F[Unit]                  = socket.setLinger(linger)
  def setIdentity(identity: Identity.Fixed): F[Unit]      = socket.setIdentity(identity)

}
