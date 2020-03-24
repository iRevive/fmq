package io.fmq.socket
package pubsub

import cats.effect.Sync
import io.fmq.address.Uri
import org.zeromq.ZMQ

final class XSubscriberSocket[F[_]: Sync] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    val uri: Uri.Complete
)(implicit protected val F: Sync[F])
    extends ConnectedSocket
    with ProducerSocket[F]
    with ConsumerSocket[F] {

  def sendSubscribe(topic: Subscriber.Topic): F[Unit] =
    send(XSubscriberSocket.Subscribe +: topic.value)

  def sendUnsubscribe(topic: Subscriber.Topic): F[Unit] =
    send(XSubscriberSocket.Unsubscribe +: topic.value)

}

object XSubscriberSocket {

  val Subscribe: Byte   = 0x01
  val Unsubscribe: Byte = 0x00

}
