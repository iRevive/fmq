package io.fmq.socket

import cats.data.NonEmptyList
import cats.effect.kernel.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.fmq.frame.{Frame, FrameDecoder}
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SocketOptions}
import org.zeromq.ZMQ

trait ConsumerSocket[F[_]] extends ConnectedSocket with SocketOptions[F] with CommonOptions.Get[F] with ReceiveOptions.Get[F] {

  /**
    * Returns `Frame.Multipart` if message is multipart. Otherwise returns `Frame.Single`.
    */
  def receiveFrame[A: FrameDecoder]: F[Frame[A]] = {

    @SuppressWarnings(Array("org.wartremover.warts.Recursion", "org.wartremover.warts.ListAppend"))
    def loop(out: List[A]): F[List[A]] =
      hasReceiveMore.ifM(receive[A].flatMap(message => loop(out :+ message)), F.pure(out))

    for {
      first <- receive[A]
      rest  <- loop(Nil)
    } yield NonEmptyList.fromList(rest).fold[Frame[A]](Frame.Single(first))(Frame.Multipart(first, _))
  }

  /**
    * Low-level API.
    *
    * The operation blocks a thread until a new message is available.
    *
    * Use `socket.receive[Array[Byte]].evalOn(blocker)` or consume messages on a blocking context in the background:
    *
    * {{{
    * import cats.effect.syntax.async._
    * import cats.effect.{Async, Concurrent, Resource}
    * import cats.effect.std.Queue
    * import fs2.Stream
    * import io.fmq.socket.ConsumerSocket
    *
    * def consume[F[_]: Async](blocker: ExecutionContext, socket: ConsumerSocket[F]): Stream[F, Array[Byte]] = {
    *   def process(queue: Queue[F, Array[Byte]]) =
    *     Stream.repeatEval(socket.receive[Array[Byte]]).evalMap(queue.offer).compile.drain
    *
    *   for {
    *     queue  <- Stream.eval(Queue.unbounded[F, Array[Byte]])
    *     _      <- Stream.resource(process(queue).backgroundOn(blocker))
    *     result <- Stream.repeatEval(queue.take)
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
    F.interruptible(many = true)(FrameDecoder[A].decode(socket.recv()))

  /**
    * Low-level API.
    *
    * Tries to receive a single message without blocking. If message is not available returns `None`.
    */
  def receiveNoWait[A: FrameDecoder]: F[Option[A]] =
    F.delay(Option(socket.recv(ZMQ.DONTWAIT)).map(FrameDecoder[A].decode))

  def hasReceiveMore: F[Boolean] =
    F.delay(socket.hasReceiveMore)

}

object ConsumerSocket {

  abstract class Connected[F[_]](implicit protected val F: Sync[F]) extends ConnectedSocket with ConsumerSocket[F]

}
