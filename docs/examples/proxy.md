---
id: proxy
title: Proxy
---

The `Proxy` connects a frontend socket to a backend socket. Conceptually, data flows from frontend to backend. 
Depending on the socket types, replies may flow in the opposite direction. 

If the capture socket is defined, the proxy shall send all messages, received on both frontend and backend, to the capture socket. 
The capture socket should be a **Publisher**, **Dealer**, **Push**, or **Pair** socket.

## Example

When the frontend is a **Router** socket, and the backend is a **Dealer** socket, 
the proxy shall act as a shared queue that collects requests from a set of clients, and distributes these fairly among a set of services. 
Requests shall be fair-queued from frontend connections and distributed evenly across backend connections. 
Replies shall automatically return to the client that made the original request.

First of all, let's introduce a `Server` that replies to the requests:

```scala mdoc:silent
import cats.effect.{Blocker, Concurrent, ContextShift}
import cats.effect.syntax.concurrent._
import cats.syntax.flatMap._
import fs2.Stream
import fs2.concurrent.Queue
import io.fmq.frame.Frame
import io.fmq.socket.reqrep.Reply

class Server[F[_]: Concurrent](socket: Reply.Socket[F], blocker: Blocker) {

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
```

Secondly, we need a `Client` that sends requests: 

```scala mdoc:silent
import cats.effect.{Sync}
import fs2.Stream
import io.fmq.pattern.RequestReply

import scala.concurrent.duration._

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
```

Also, we can add a `MessageObserver` that observers all proxied messages:

```scala mdoc:silent
import cats.effect.{Sync}
import fs2.Stream
import io.fmq.socket.pipeline.Pull

class MessageObserver[F[_]: Sync: Timer](pull: Pull.Socket[F]) {

  def start: Stream[F, Unit] =
    Stream
      .repeatEval(pull.receiveFrame[String])
      .evalTap(frame => log(s"Control message $frame"))
      .drain

  private def log(message: => String): F[Unit] =
    Sync[F].delay(println(message))

}
``` 

And the `ProxyDemo` to put everything together:

```scala mdoc:silent
import cats.effect.{Blocker, Concurrent, ContextShift, Resource}
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Stream
import io.fmq.Context
import io.fmq.options.Identity
import io.fmq.pattern.RequestReply
import io.fmq.proxy.Control
import io.fmq.socket.pipeline.{Pull, Push}
import io.fmq.socket.reqrep.{Dealer, Reply, Request, Router}
import io.fmq.syntax.literals._

class ProxyDemo[F[_]: Concurrent: Timer](context: Context[F], blocker: Blocker) {

  private val frontendUri = inproc"://frontend"
  private val backendUri  = inproc"://backend"
  private val controlUri  = inproc"://control"

  private val identity = Identity.utf8String("my-identity")

  val program: Stream[F, Unit] =
    for {
      (proxy, observer, client, server) <- Stream.resource(appResource) // initialize app components
      _                                 <- Stream.resource(proxy.start(blocker)) // start proxy
      _                                 <- Stream(server.serve, client.start, observer.start).parJoinUnbounded // start components
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
```

At the edge of out program we define our effect, `cats.effect.IO` in this case, and ask to evaluate the effects:

```scala mdoc:silent
import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.syntax.functor._
import io.fmq.Context

object ProxyApp extends IOApp {

 override def run(args: List[String]): IO[ExitCode] =
   Blocker[IO]
     .flatMap(blocker => Context.create[IO](ioThreads = 1, blocker).tupleRight(blocker))
     .use { case (ctx, blocker) => new ProxyDemo[IO](ctx, blocker).program.compile.drain.as(ExitCode.Success) }

}
```

The output will be:
```text
Client. Sending request [0]
Server. Received request Single(0)
Client. Received response Single(0-response)
Control message Multipart(my-identity,NonEmptyList(, 0))
Control message Multipart(my-identity,NonEmptyList(, 0-response))

Client. Sending request [1]
Control message Multipart(my-identity,NonEmptyList(, 1))
Server. Received request Single(1)
Client. Received response Single(1-response)
Control message Multipart(my-identity,NonEmptyList(, 1-response))
```