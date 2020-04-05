package io.fmq.socket

import cats.effect.Sync
import io.fmq.address.Uri
import org.zeromq.ZMQ

trait ConnectedSocket {
  def uri: Uri.Complete
  protected[fmq] def socket: ZMQ.Socket
}

abstract class BidirectionalSocket[F[_]](implicit protected val F: Sync[F])
    extends ConnectedSocket
    with ProducerSocket[F]
    with ConsumerSocket[F]
