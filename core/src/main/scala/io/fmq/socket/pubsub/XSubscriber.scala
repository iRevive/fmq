package io.fmq.socket.pubsub

import cats.effect.{Blocker, ContextShift, Sync}
import io.fmq.address.Uri
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SendOptions, SocketOptions}
import io.fmq.socket.{Connect, ProducerConsumerSocket, SocketFactory}
import org.zeromq.ZMQ

final class XSubscriber[F[_]: Sync: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    protected val blocker: Blocker
) extends Connect[F, XSubscriber.Socket]
    with SocketOptions[F]
    with CommonOptions.All[F]
    with SendOptions.All[F]
    with ReceiveOptions.All[F]

object XSubscriber {

  val Subscribe: Byte   = 0x01
  val Unsubscribe: Byte = 0x00

  final class Socket[F[_]: Sync] private[XSubscriber] (
      protected[fmq] val socket: ZMQ.Socket,
      val uri: Uri.Complete
  ) extends ProducerConsumerSocket[F] {

    def sendSubscribe(topic: Subscriber.Topic): F[Unit] =
      send(XSubscriber.Subscribe +: topic.value)

    def sendUnsubscribe(topic: Subscriber.Topic): F[Unit] =
      send(XSubscriber.Unsubscribe +: topic.value)

  }

  implicit val xSubscriberSocketFactory: SocketFactory[XSubscriber.Socket] = new SocketFactory[XSubscriber.Socket] {

    override def create[F[_]: Sync](socket: ZMQ.Socket, uri: Uri.Complete): XSubscriber.Socket[F] =
      new XSubscriber.Socket[F](socket, uri)
  }

}
