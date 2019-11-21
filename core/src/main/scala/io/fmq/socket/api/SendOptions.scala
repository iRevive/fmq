package io.fmq.socket.api

import io.fmq.domain.SendTimeout

trait SendOptions[F[_]] extends CommonOptions[F] {

  def sendTimeout: F[SendTimeout]                   = F.delay(SendTimeout.fromInt(socket.getSendTimeOut))
  def setSendTimeout(timeout: SendTimeout): F[Unit] = F.void(F.delay(socket.setSendTimeOut(timeout.value)))

}
