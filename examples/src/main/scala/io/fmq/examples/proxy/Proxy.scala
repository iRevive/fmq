package io.fmq.examples.proxy

import cats.effect.{Blocker, Concurrent, ContextShift, ExitCode, IO, IOApp, Resource, Sync, Timer}
import cats.effect.syntax.concurrent._
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Stream
import fs2.concurrent.Queue
import io.fmq.Context
import io.fmq.frame.Frame
import io.fmq.options.Identity
import io.fmq.pattern.RequestReply
import io.fmq.proxy.Control
import io.fmq.socket.pipeline.{Pull, Push}
import io.fmq.socket.reqrep.{Dealer, Reply, Request, Router}
import io.fmq.syntax.literals._

import scala.concurrent.duration._

object ProxyApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    Blocker[IO]
      .flatMap(blocker => Context.create[IO](ioThreads = 1, blocker).tupleRight(blocker))
      .use { case (ctx, blocker) => new ProxyDemo[IO](ctx, blocker).program.compile.drain.as(ExitCode.Success) }

}

class ProxyDemo[F[_]: Concurrent: ContextShift: Timer](context: Context[F], blocker: Blocker) {

  private val frontendUri = inproc"://frontend"
  private val backendUri  = inproc"://backend"
  private val controlUri  = inproc"://control"

  private val identity = Identity.utf8String("my-identity")

  val program: Stream[F, Unit] =
    for {
      (proxy, observer, client, server) <- Stream.resource(appResource)
      _                                 <- Stream.resource(proxy.start(blocker))
      _                                 <- Stream(server.serve, client.start, observer.start).parJoinUnbounded
    } yield ()

  private def appResource =
    for {
      (router, dealer) <- createProxySockets
      (pull, push)     <- createControlSockets
      (request, reply) <- createReqRepSockets
      requestReply     <- RequestReply.create[F](blocker, request, queueSize = 128)
      control          <- Resource.pure(Control.push(push))
      proxy            <- context.proxy.bidirectional(router, dealer, Some(control), Some(control))
    } yield (proxy, new MessageObserver[F](pull), new Client[F](requestReply), new Server[F](reply, blocker))

  private def createProxySockets: Resource[F, (Router.Socket[F], Dealer.Socket[F])] =
    for {
      router <- Resource.suspend(context.createRouter.map(_.bind(frontendUri)))
      dealer <- Resource.suspend(context.createDealer.map(_.bind(backendUri)))
    } yield (router, dealer)

  private def createControlSockets: Resource[F, (Pull.Socket[F], Push.Socket[F])] =
    for {
      pull <- Resource.suspend(context.createPull.map(_.bind(controlUri)))
      push <- Resource.suspend(context.createPush.map(_.connect(controlUri)))
    } yield (pull, push)

  private def createReqRepSockets: Resource[F, (Request.Socket[F], Reply.Socket[F])] =
    for {
      request <- Resource.suspend(context.createRequest.flatTap(_.setIdentity(identity)).map(_.connect(frontendUri)))
      reply   <- Resource.suspend(context.createReply.map(_.connect(backendUri)))
    } yield (request, reply)

}

@SuppressWarnings(Array("org.wartremover.warts.StringPlusAny"))
class Server[F[_]: Concurrent: ContextShift](socket: Reply.Socket[F], blocker: Blocker) {

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
      case Frame.Single(value)       => socket.sendFrame(Frame.Single(s"$value-response"))
      case Frame.Multipart(value, _) => socket.sendFrame(Frame.Single(s"$value-multipart-response"))
    }

    log(s"Server. Received request $request") >> action
  }

  private def log(message: => String): F[Unit] =
    Concurrent[F].delay(println(message))

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

@SuppressWarnings(Array("org.wartremover.warts.StringPlusAny"))
class MessageObserver[F[_]: Sync: Timer](pull: Pull.Socket[F]) {

  def start: Stream[F, Unit] =
    Stream
      .repeatEval(pull.receiveFrame[String])
      .evalTap(frame => log(s"Control message $frame"))
      .drain

  private def log(message: => String): F[Unit] =
    Sync[F].delay(println(message))

}
