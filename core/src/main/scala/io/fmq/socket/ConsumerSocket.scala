package io.fmq.socket

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.fmq.address.{Address, Complete, Protocol, Uri}
import io.fmq.frame.{Frame, FrameDecoder}
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SocketOptions}
import org.zeromq.ZMQ

trait ConsumerSocket[F[_], P <: Protocol, A <: Address]
    extends ConnectedSocket[P, A]
    with SocketOptions[F]
    with CommonOptions.Get[F]
    with ReceiveOptions.Get[F] {

  /**
    * Returns `Frame.Multipart` if message is multipart. Otherwise returns `Frame.Single`.
    */
  def receiveFrame[B: FrameDecoder]: F[Frame[B]] = {

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    def loop(out: List[B]): F[List[B]] =
      hasReceiveMore.ifM(receive[B].flatMap(message => loop(out :+ message)), F.pure(out))

    for {
      first <- receive[B]
      rest  <- loop(Nil)
    } yield NonEmptyList.fromList(rest).fold[Frame[B]](Frame.Single(first))(Frame.Multipart(first, _))
  }

  /**
    * The operation blocks a thread until a new message is available.
    *
    * Use `blocker.blockOn(socket.receive[Array[Byte]])` or consume messages on a blocking context in the background:
    *
    * {{{
    * import cats.effect.syntax.concurrent._
    * import cats.effect.{Blocker, Concurrent, ContextShift, Resource}
    * import fs2.Stream
    * import fs2.concurrent.Queue
    * import io.fmq.ConsumerSocket
    *
    * def consume[F[_]: Concurrent: ContextShift](blocker: Blocker, socket: ConsumerSocket[F]): Stream[F, Array[Byte]] = {
    *   def process(queue: Queue[F, Array[Byte]]) =
    *     blocker.blockOn(Stream.repeatEval(socket.receive[Array[Byte]]).through(queue.enqueue).compile.drain)
    *
    *   for {
    *     queue  <- Stream.eval(Queue.unbounded[F, Array[Byte]])
    *     _      <- Stream.resource(process(queue).background)
    *     result <- queue.dequeue
    *   } yield result
    * }
    * }}}
    */
  def receive[B: FrameDecoder]: F[B] =
    F.delay(FrameDecoder[B].decode(socket.recv()))

  def receiveNoWait[B: FrameDecoder]: F[Option[B]] =
    F.delay(Option(socket.recv(ZMQ.DONTWAIT)).map(FrameDecoder[B].decode))

  def hasReceiveMore: F[Boolean] =
    F.delay(socket.hasReceiveMore)

}

object ConsumerSocket extends SocketTypeAlias[ConsumerSocket] {

  def create[F[_]: Sync, P <: Protocol, A <: Address: Complete[P, *]](s: ZMQ.Socket, u: Uri[P, A]): ConsumerSocket[F, P, A] =
    new ConnectedSocket[P, A] with ConsumerSocket[F, P, A] {
      override def uri: Uri[P, A] = u

      // $COVERAGE-OFF$
      override protected def complete: Complete[P, A] = implicitly[Complete[P, A]]
      // $COVERAGE-ON$

      override protected def F: Sync[F] = implicitly[Sync[F]]

      override private[fmq] def socket: ZMQ.Socket = s
    }

}
