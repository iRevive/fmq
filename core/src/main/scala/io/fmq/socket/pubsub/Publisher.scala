package io.fmq.socket
package pubsub

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import cats.syntax.functor._
import io.fmq.address.Uri
import io.fmq.socket.api.{CommonOptions, SendOptions, SocketOptions}
import io.fmq.socket.internal.Bind
import org.zeromq.ZMQ

final class Publisher[F[_]: ContextShift] private[fmq] (
    protected[fmq] val socket: ZMQ.Socket,
    blocker: Blocker
)(implicit protected val F: Sync[F])
    extends SocketOptions[F]
    with CommonOptions.All[F]
    with SendOptions.All[F] {

  def bind(uri: Uri.Complete): Resource[F, ProducerSocket[F]] =
    Bind.bind[F](uri, socket, blocker).as(ProducerSocket.create(socket, uri))

  def bindToRandomPort(uri: Uri.Incomplete.TCP): Resource[F, ProducerSocket[F]] =
    for {
      completeUri <- Bind.bindToRandomPort[F](uri, socket, blocker)
    } yield ProducerSocket.create(socket, completeUri)

}
