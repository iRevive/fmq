package io.fmq.socket
package pipeline

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.Uri
import io.fmq.socket.api.{CommonOptions, SendOptions, SocketOptions}
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class Push[F[_]: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    blocker: Blocker
)(implicit protected val F: Sync[F])
    extends SocketOptions[F]
    with CommonOptions.All[F]
    with SendOptions.All[F] {

  def connect(uri: Uri.Complete): Resource[F, ProducerSocket[F]] =
    Bind.connect[F](uri, socket, blocker).as(ProducerSocket.create[F](socket, uri))

}
