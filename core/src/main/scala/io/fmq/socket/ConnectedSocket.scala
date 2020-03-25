package io.fmq.socket

import cats.effect.Sync
import io.fmq.address.Uri
import org.zeromq.ZMQ

trait ConnectedSocket {
  def uri: Uri.Complete
}

abstract class ProducerConsumerSocket[F[_]](
    protected[fmq] val socket: ZMQ.Socket,
    val uri: Uri.Complete
)(implicit protected val F: Sync[F])
    extends ConnectedSocket
    with ProducerSocket[F]
    with ConsumerSocket[F]
