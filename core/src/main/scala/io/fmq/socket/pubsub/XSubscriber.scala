package io.fmq.socket.pubsub

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.Uri
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SocketOptions}
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class XSubscriber[F[_]: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    blocker: Blocker
)(implicit protected val F: Sync[F])
    extends SocketOptions[F]
    with CommonOptions.All[F]
    with ReceiveOptions.All[F] {

  def connect(uri: Uri.Complete): Resource[F, XSubscriberSocket[F]] =
    Bind.connect[F](uri, socket, blocker).as(new XSubscriberSocket[F](socket, uri))

}
