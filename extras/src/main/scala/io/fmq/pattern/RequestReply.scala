package io.fmq.pattern

import cats.effect.concurrent.Deferred
import cats.effect.syntax.concurrent._
import cats.effect.{Blocker, Concurrent, ContextShift, Resource}
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.concurrent.Queue
import io.fmq.frame.{Frame, FrameDecoder, FrameEncoder}
import io.fmq.socket.reqrep.Request

class RequestReply[F[_]: Concurrent] private (
    socket: Request.Socket[F],
    requestQueue: Queue[F, F[Unit]]
) {

  def submit[Req: FrameEncoder, Rep: FrameDecoder](frame: Frame[Req]): F[Frame[Rep]] = {

    def background(promise: Deferred[F, Frame[Rep]]): F[Unit] =
      for {
        _        <- socket.sendFrame[Req](frame)
        response <- socket.receiveFrame[Rep]
        _        <- promise.complete(response)
      } yield ()

    for {
      promise <- Deferred[F, Frame[Rep]]
      _       <- requestQueue.enqueue1(background(promise))
      result  <- promise.get
    } yield result
  }

}

object RequestReply {

  def create[F[_]: Concurrent: ContextShift](
      blocker: Blocker,
      socket: Request.Socket[F],
      queueSize: Int
  ): Resource[F, RequestReply[F]] =
    for {
      queue <- Resource.liftF(Queue.bounded[F, F[Unit]](queueSize))
      _     <- blocker.blockOn(queue.dequeue.evalMap(identity).compile.drain).background
    } yield new RequestReply[F](socket, queue)

}
