package io.fmq.socket

import cats.effect.Sync
import io.fmq.address.Uri

trait ConnectedSocket {
  def uri: Uri.Complete
}

abstract class ProducerConsumerSocket[F[_]](implicit protected val F: Sync[F])
    extends ConnectedSocket
    with ProducerSocket[F]
    with ConsumerSocket[F]
