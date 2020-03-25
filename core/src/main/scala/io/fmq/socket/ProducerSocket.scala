package io.fmq.socket

import cats.effect.Sync
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import io.fmq.address.Uri
import io.fmq.frame.{Frame, FrameEncoder}
import io.fmq.socket.api.{CommonOptions, SendOptions, SocketOptions}
import org.zeromq.ZMQ

trait ProducerSocket[F[_]] extends ConnectedSocket with SocketOptions[F] with CommonOptions.Get[F] with SendOptions.Get[F] {

  def sendFrame[A: FrameEncoder](frame: Frame[A]): F[Unit] =
    frame match {
      case Frame.Single(value)   => send(value)
      case m: Frame.Multipart[A] => sendMultipart(m)
    }

  def sendMultipart[A: FrameEncoder](frame: Frame.Multipart[A]): F[Unit] = {
    val parts = frame.parts

    for {
      _ <- parts.init.traverse(sendMore[A])
      _ <- send(parts.last)
    } yield ()
  }

  def send[A: FrameEncoder](value: A): F[Unit] =
    F.delay(socket.send(FrameEncoder[A].encode(value))).void

  def sendMore[A: FrameEncoder](value: A): F[Unit] =
    F.delay(socket.sendMore(FrameEncoder[A].encode(value))).void

}

object ProducerSocket {

  abstract class Connected[F[_]](
      protected[fmq] val socket: ZMQ.Socket,
      val uri: Uri.Complete
  )(implicit protected val F: Sync[F])
      extends ConnectedSocket
      with ProducerSocket[F]

}
