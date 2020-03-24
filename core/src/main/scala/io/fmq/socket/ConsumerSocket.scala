package io.fmq.socket

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.fmq.address.Uri
import io.fmq.frame.{Frame, FrameDecoder}
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SocketOptions}
import org.zeromq.ZMQ

trait ConsumerSocket[F[_]] extends ConnectedSocket with SocketOptions[F] with CommonOptions.Get[F] with ReceiveOptions.Get[F] {

  /**
    * Returns `Frame.Multipart` if message is multipart. Otherwise returns `Frame.Single`.
    */
  def receiveFrame[A: FrameDecoder]: F[Frame[A]] = {

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    def loop(out: List[A]): F[List[A]] =
      hasReceiveMore.ifM(receive[A].flatMap(message => loop(out :+ message)), F.pure(out))

    for {
      first <- receive[A]
      rest  <- loop(Nil)
    } yield NonEmptyList.fromList(rest).fold[Frame[A]](Frame.Single(first))(Frame.Multipart(first, _))
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
    * import io.fmq.socket.ConsumerSocket
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
    *
    * Or use `io.fmq.pattern.BackgroundConsumer` from `fmq-extras` project:
    * {{{
    * val data: Stream[F, Frame[Array[Byte]] = BackgroundConsumer.consume[F, Array[Byte]](blocker, socket, 128)
    * }}}
    */
  def receive[A: FrameDecoder]: F[A] =
    F.delay(FrameDecoder[A].decode(socket.recv()))

  def receiveNoWait[A: FrameDecoder]: F[Option[A]] =
    F.delay(Option(socket.recv(ZMQ.DONTWAIT)).map(FrameDecoder[A].decode))

  def hasReceiveMore: F[Boolean] =
    F.delay(socket.hasReceiveMore)

}

object ConsumerSocket {

  def create[F[_]: Sync](s: ZMQ.Socket, u: Uri.Complete): ConsumerSocket[F] =
    new ConsumerSocket[F] {
      override def uri: Uri.Complete = u

      override protected def F: Sync[F] = implicitly[Sync[F]]

      override private[fmq] def socket: ZMQ.Socket = s
    }

}
