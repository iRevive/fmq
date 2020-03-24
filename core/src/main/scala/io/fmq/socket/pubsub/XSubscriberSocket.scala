package io.fmq.socket
package pubsub

import cats.effect.Sync
import io.fmq.address.{Address, Complete, Protocol, Uri}
import org.zeromq.ZMQ

class XSubscriberSocket[F[_]: Sync, P <: Protocol, A <: Address] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    val uri: Uri[P, A]
)(implicit protected val F: Sync[F], protected val complete: Complete[P, A])
    extends ConnectedSocket[P, A]
    with ProducerSocket[F, P, A]
    with ConsumerSocket[F, P, A] {

  def sendSubscribe(topic: Subscriber.Topic): F[Unit] =
    send(XSubscriberSocket.Subscribe +: topic.value)

  def sendUnsubscribe(topic: Subscriber.Topic): F[Unit] =
    send(XSubscriberSocket.Unsubscribe +: topic.value)

}

object XSubscriberSocket {

  type TCP[F[_]]    = XSubscriberSocket[F, Protocol.TCP, Address.Full]
  type InProc[F[_]] = XSubscriberSocket[F, Protocol.InProc, Address.HostOnly]

  val Subscribe: Byte   = 0x01
  val Unsubscribe: Byte = 0x00

}