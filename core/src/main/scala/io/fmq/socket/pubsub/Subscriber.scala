package io.fmq.socket
package pubsub

import java.nio.charset.StandardCharsets

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.Uri
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SocketOptions}
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class Subscriber[F[_]: ContextShift] private[fmq] (
    val topic: Subscriber.Topic,
    protected[fmq] val socket: ZMQ.Socket,
    blocker: Blocker
)(implicit protected val F: Sync[F])
    extends SocketOptions[F]
    with CommonOptions.All[F]
    with ReceiveOptions.All[F] {

  def connect(uri: Uri.Complete): Resource[F, ConsumerSocket[F]] =
    Bind.connect[F](uri, socket, blocker).as(ConsumerSocket.create[F](socket, uri))

}

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

}
