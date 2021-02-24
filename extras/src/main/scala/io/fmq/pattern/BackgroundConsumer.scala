package io.fmq.pattern

import cats.effect.kernel.Async
import cats.effect.std.Queue
import cats.effect.syntax.async._
import fs2.Stream
import io.fmq.frame.{Frame, FrameDecoder}
import io.fmq.socket.ConsumerSocket

import scala.concurrent.ExecutionContext

object BackgroundConsumer {

  /**
    * Consumes messages in background on a dedicated blocking execution context
    */
  def consume[F[_]: Async, A: FrameDecoder](
      blocker: ExecutionContext,
      socket: ConsumerSocket[F],
      queueSize: Int
  ): Stream[F, Frame[A]] = {
    def process(queue: Queue[F, Frame[A]]): F[Unit] =
      Stream.repeatEval(socket.receiveFrame[A]).evalMap(queue.offer).compile.drain

    for {
      queue  <- Stream.eval(Queue.bounded[F, Frame[A]](queueSize))
      _      <- Stream.resource(process(queue).backgroundOn(blocker))
      result <- Stream.repeatEval(queue.take)
    } yield result
  }

}
