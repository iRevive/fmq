package io.fmq.socket.pubsub

import java.nio.charset.StandardCharsets

import cats.effect.kernel.Sync
import io.fmq.address.Uri
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SocketFactory, SocketOptions}
import io.fmq.socket.{Connectivity, ConsumerSocket}
import org.zeromq.ZMQ

final class Subscriber[F[_]: Sync] private[fmq] (
    val topic: Subscriber.Topic,
    protected[fmq] val socket: ZMQ.Socket
) extends Connectivity.All[F, Subscriber.Socket]
    with SocketOptions[F]
    with CommonOptions.All[F]
    with ReceiveOptions.All[F]

object Subscriber {

  sealed trait Topic {
    def value: Array[Byte]
  }

  object Topic {

    @SuppressWarnings(Array("org.wartremover.warts.ArrayEquals"))
    final case class Bytes(value: Array[Byte]) extends Topic

    final case object All extends Topic {
      override val value: Array[Byte] = Array.empty
    }

    def utf8String(value: String): Topic.Bytes = Bytes(value.getBytes(StandardCharsets.UTF_8))

  }

  final class Socket[F[_]: Sync] private[Subscriber] (
      protected[fmq] val socket: ZMQ.Socket,
      val uri: Uri.Complete
  ) extends ConsumerSocket.Connected[F]

  implicit val subscriberSocketFactory: SocketFactory[Subscriber.Socket] = new SocketFactory[Subscriber.Socket] {

    override def create[F[_]: Sync](socket: ZMQ.Socket, uri: Uri.Complete): Subscriber.Socket[F] =
      new Subscriber.Socket[F](socket, uri)

  }

}
