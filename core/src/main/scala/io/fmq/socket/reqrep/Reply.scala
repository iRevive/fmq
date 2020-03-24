package io.fmq.socket
package reqrep

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.Uri
import io.fmq.socket.api.{CommonOptions, SendOptions, SocketOptions}
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class Reply[F[_]: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    blocker: Blocker
)(implicit protected val F: Sync[F])
    extends SocketOptions[F]
    with CommonOptions.All[F]
    with SendOptions.All[F] {

  def bind(uri: Uri.Complete): Resource[F, ReplySocket[F]] =
    Bind.bind[F](uri, socket, blocker).as(new ReplySocket(socket, uri))

  def bindToRandomPort(uri: Uri.Incomplete.TCP): Resource[F, ReplySocket[F]] =
    for {
      completeUri <- Bind.bindToRandomPort[F](uri, socket, blocker)
    } yield new ReplySocket(socket, completeUri)

}
