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

  def sendMultipart[a: FrameEncoder](frame: Frame.Multipart[a]): F[Unit] = {
    val parts = frame.parts

    for {
      _ <- parts.init.traverse(sendMore[a])
      _ <- send(parts.last)
    } yield ()
  }

  def send[A: FrameEncoder](value: A): F[Unit] =
    F.delay(socket.send(FrameEncoder[A].encode(value))).void

  def sendMore[A: FrameEncoder](value: A): F[Unit] =
    F.delay(socket.sendMore(FrameEncoder[A].encode(value))).void

}

object ProducerSocket {

  def create[F[_]: Sync](s: ZMQ.Socket, u: Uri.Complete): ProducerSocket[F] =
    new ProducerSocket[F] {
      override def uri: Uri.Complete = u

      override protected def F: Sync[F] = implicitly[Sync[F]]

      override private[fmq] def socket: ZMQ.Socket = s
    }

}
