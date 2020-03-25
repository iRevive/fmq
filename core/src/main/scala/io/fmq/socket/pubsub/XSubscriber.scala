package io.fmq.socket.pubsub

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.Uri
import io.fmq.socket.ProducerConsumerSocket
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SendOptions, SocketOptions}
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class XSubscriber[F[_]: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    blocker: Blocker
)(implicit protected val F: Sync[F])
    extends SocketOptions[F]
    with CommonOptions.All[F]
    with SendOptions.All[F]
    with ReceiveOptions.All[F] {

  def connect(uri: Uri.Complete): Resource[F, XSubscriber.Socket[F]] =
    Bind.connect[F](uri, socket, blocker).as(new XSubscriber.Socket[F](socket, uri))

}

object XSubscriber {

  val Subscribe: Byte   = 0x01
  val Unsubscribe: Byte = 0x00

  final class Socket[F[_]: Sync] private[XSubscriber] (
      socket: ZMQ.Socket,
      uri: Uri.Complete
  ) extends ProducerConsumerSocket[F](socket, uri) {

    def sendSubscribe(topic: Subscriber.Topic): F[Unit] =
      send(XSubscriber.Subscribe +: topic.value)

    def sendUnsubscribe(topic: Subscriber.Topic): F[Unit] =
      send(XSubscriber.Unsubscribe +: topic.value)

  }

}
