package io.fmq.examples.reqrep

import java.util.concurrent.Executors

import cats.effect.std.Queue
import cats.effect.syntax.async._
import cats.effect._
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Stream
import io.fmq.Context
import io.fmq.frame.Frame
import io.fmq.pattern.RequestReply
import io.fmq.socket.reqrep.Reply
import io.fmq.syntax.literals._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object ReqRepApp extends IOApp.Simple {

  override def run: IO[Unit] =
    blockingContext
      .flatMap(blocker => Context.create[IO](ioThreads = 1).tupleRight(blocker))
      .use { case (ctx, blocker) => new ReqRepDemo[IO](ctx, blocker).program.compile.drain }

  private def blockingContext: Resource[IO, ExecutionContext] =
    Resource
      .make(IO.delay(Executors.newCachedThreadPool()))(e => IO.delay(e.shutdown()))
      .map(ExecutionContext.fromExecutor)

}

class ReqRepDemo[F[_]: Async](context: Context[F], blocker: ExecutionContext) {

  private val uri = tcp_i"://localhost"

  private val appResource =
    for {
      reply      <- Resource.suspend(context.createReply.map(_.bindToRandomPort(uri)))
      request    <- Resource.suspend(context.createRequest.map(_.connect(reply.uri)))
      dispatcher <- RequestReply.create[F](blocker, request, 100)
    } yield (reply, dispatcher)

  val program: Stream[F, Unit] =
    Stream
      .resource(appResource)
      .flatMap {
        case (replySocket, dispatcher) =>
          val server = new Server(replySocket, blocker)
          val client = new Client(dispatcher)

          Stream(server.serve, client.start).parJoinUnbounded
      }

}

@SuppressWarnings(Array("org.wartremover.warts.StringPlusAny"))
class Server[F[_]: Async](socket: Reply.Socket[F], blocker: ExecutionContext) {

  def serve: Stream[F, Unit] = {
    def process(queue: Queue[F, Frame[String]]) =
      Stream.repeatEval(socket.receiveFrame[String]).evalMap(queue.offer).compile.drain

    for {
      queue  <- Stream.eval(Queue.unbounded[F, Frame[String]])
      _      <- Stream.resource(process(queue).backgroundOn(blocker))
      result <- Stream.repeatEval(queue.take).evalMap(processRequest)
    } yield result
  }

  private def processRequest(request: Frame[String]): F[Unit] = {
    val action = request match {
      case Frame.Single(value)       => socket.sendFrame(Frame.Single(s"$value-response"))
      case Frame.Multipart(value, _) => socket.sendFrame(Frame.Single(s"$value-multipart-response"))
    }

    log(s"Server. Received request $request") >> action
  }

  private def log(message: => String): F[Unit] =
    Async[F].delay(println(message))

}

@SuppressWarnings(Array("org.wartremover.warts.StringPlusAny"))
class Client[F[_]: Async](dispatcher: RequestReply[F]) {

  def start: Stream[F, Unit] =
    Stream
      .awakeEvery[F](1.second)
      .zipWithIndex
      .evalMap {
        case (_, idx) =>
          log(s"Client. Sending request [$idx]") >> dispatcher.submit[String, String](Frame.Single(idx.toString))
      }
      .evalMap(response => log(s"Client. Received response $response"))

  private def log(message: => String): F[Unit] =
    Async[F].delay(println(message))

}
