package io.fmq.socket.api

import io.fmq.domain.ReceiveTimeout

trait ReceiveOptions[F[_]] extends CommonOptions[F] {

  def receiveTimeout: F[ReceiveTimeout]                   = F.delay(ReceiveTimeout.fromInt(socket.getReceiveTimeOut))
  def setReceiveTimeout(timeout: ReceiveTimeout): F[Unit] = F.void(F.delay(socket.setReceiveTimeOut(timeout.value)))

  def hasReceiveMore: F[Boolean] = F.delay(socket.hasReceiveMore)

}
