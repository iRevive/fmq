package io.fmq.pattern

import cats.effect.syntax.concurrent._
import cats.effect.{Blocker, Concurrent, ContextShift}
import fs2.Stream
import fs2.concurrent.Queue
import io.fmq.frame.{Frame, FrameDecoder}
import io.fmq.socket.ConsumerSocket

object BackgroundConsumer {

  /**
    * Consumes messages in background on a dedicated blocking thread
    */
  def consume[F[_]: Concurrent: ContextShift, A: FrameDecoder](
      blocker: Blocker,
      socket: ConsumerSocket[F],
      queueSize: Int
  ): Stream[F, Frame[A]] = {
    def process(queue: Queue[F, Frame[A]]): F[Unit] =
      blocker.blockOn(Stream.repeatEval(socket.receiveFrame[A]).through(queue.enqueue).compile.drain)

    for {
      queue  <- Stream.eval(Queue.bounded[F, Frame[A]](queueSize))
      _      <- Stream.resource(process(queue).background)
      result <- queue.dequeue
    } yield result
  }

}
