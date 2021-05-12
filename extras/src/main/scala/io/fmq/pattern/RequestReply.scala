package io.fmq.pattern

import cats.effect.std.Queue
import cats.effect.syntax.async._
import cats.effect.{Async, Deferred, Resource}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.fmq.frame.{Frame, FrameDecoder, FrameEncoder}
import io.fmq.socket.reqrep.Request

import scala.concurrent.ExecutionContext

class RequestReply[F[_]: Async] private (
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
      _       <- requestQueue.offer(background(promise))
      result  <- promise.get
    } yield result
  }

}

object RequestReply {

  def create[F[_]: Async](
      blocker: ExecutionContext,
      socket: Request.Socket[F],
      queueSize: Int
  ): Resource[F, RequestReply[F]] =
    for {
      queue <- Resource.eval(Queue.bounded[F, F[Unit]](queueSize))
      _     <- fs2.Stream.repeatEval(queue.take).evalMap(identity).compile.drain.backgroundOn(blocker)
    } yield new RequestReply[F](socket, queue)

}
