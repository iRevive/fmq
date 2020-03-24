package io.fmq.socket
package reqrep

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.Uri
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SocketOptions}
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class Request[F[_]: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    blocker: Blocker
)(implicit protected val F: Sync[F])
    extends SocketOptions[F]
    with CommonOptions.All[F]
    with ReceiveOptions.All[F] {

  def connect(uri: Uri.Complete): Resource[F, RequestSocket[F]] =
    Bind.connect[F](uri, socket, blocker).as(new reqrep.RequestSocket[F](socket, uri))

}
