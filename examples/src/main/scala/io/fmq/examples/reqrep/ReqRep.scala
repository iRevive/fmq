package io.fmq.examples.reqrep

import cats.effect.syntax.concurrent._
import cats.effect.{Blocker, Concurrent, ContextShift, ExitCode, IO, IOApp, Sync, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Stream
import fs2.concurrent.Queue
import io.fmq.Context
import io.fmq.address.{Address, Host, Uri}
import io.fmq.frame.Frame
import io.fmq.pattern.RequestReply
import io.fmq.socket.reqrep.ReplySocket

import scala.concurrent.duration._

object ReqRep extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    Blocker[IO]
      .flatMap(blocker => Context.create[IO](ioThreads = 1, blocker).tupleRight(blocker))
      .use { case (ctx, blocker) => new ReqRepDemo[IO](ctx, blocker).program.compile.drain.as(ExitCode.Success) }

}

class ReqRepDemo[F[_]: Concurrent: ContextShift: Timer](context: Context[F], blocker: Blocker) {

  private val uri = Uri.Incomplete.TCP(Address.HostOnly(Host.Fixed("localhost")))

  private val appResource =
    for {
      reply      <- context.createReply.flatMap(_.bindToRandomPort(uri))
      request    <- context.createRequest.flatMap(_.connect(reply.uri))
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
class Server[F[_]: Concurrent: ContextShift](socket: ReplySocket[F], blocker: Blocker) {

  def serve: Stream[F, Unit] = {
    def process(queue: Queue[F, Frame[String]]) =
      blocker.blockOn(Stream.repeatEval(socket.receiveFrame[String]).through(queue.enqueue).compile.drain)

    for {
      queue  <- Stream.eval(Queue.unbounded[F, Frame[String]])
      _      <- Stream.resource(process(queue).background)
      result <- queue.dequeue.evalMap(processRequest)
    } yield result
  }

  private def processRequest(request: Frame[String]): F[Unit] = {
    val action = request match {
      case Frame.Single(value) => socket.sendFrame(Frame.Single(s"$value-response"))
      case Frame.Multipart(value, _) => socket.sendFrame(Frame.Single(s"$value-multipart-response"))
    }

    log(s"Server. Received request $request") >> action
  }

  private def log(message: => String): F[Unit] =
    Sync[F].delay(println(message))

}

@SuppressWarnings(Array("org.wartremover.warts.StringPlusAny"))
class Client[F[_]: Sync: Timer](dispatcher: RequestReply[F]) {

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
    Sync[F].delay(println(message))

}
