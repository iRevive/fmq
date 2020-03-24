package io.fmq.socket

import cats.effect.Sync
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import io.fmq.address.{Address, Complete, Protocol, Uri}
import io.fmq.frame.{Frame, FrameEncoder}
import io.fmq.socket.api.{CommonOptions, SendOptions, SocketOptions}
import org.zeromq.ZMQ

trait ProducerSocket[F[_], P <: Protocol, A <: Address]
    extends ConnectedSocket[P, A]
    with SocketOptions[F]
    with CommonOptions.Get[F]
    with SendOptions.Get[F] {

  def sendFrame[B: FrameEncoder](frame: Frame[B]): F[Unit] =
    frame match {
      case Frame.Single(value)   => send(value)
      case m: Frame.Multipart[B] => sendMultipart(m)
    }

  def sendMultipart[B: FrameEncoder](frame: Frame.Multipart[B]): F[Unit] = {
    val parts = frame.parts

    for {
      _ <- parts.init.traverse(sendMore[B])
      _ <- send(parts.last)
    } yield ()
  }

  def send[B: FrameEncoder](value: B): F[Unit] =
    F.delay(socket.send(FrameEncoder[B].encode(value))).void

  def sendMore[B: FrameEncoder](value: B): F[Unit] =
    F.delay(socket.sendMore(FrameEncoder[B].encode(value))).void

}

object ProducerSocket extends SocketTypeAlias[ProducerSocket] {

  def create[F[_]: Sync, P <: Protocol, A <: Address: Complete[P, *]](s: ZMQ.Socket, u: Uri[P, A]): ProducerSocket[F, P, A] =
    new ConnectedSocket[P, A] with ProducerSocket[F, P, A] {
      override def uri: Uri[P, A] = u

      // $COVERAGE-OFF$
      override protected def complete: Complete[P, A] = implicitly[Complete[P, A]]
      // $COVERAGE-ON$

      override protected def F: Sync[F] = implicitly[Sync[F]]

      override private[fmq] def socket: ZMQ.Socket = s
    }

}
