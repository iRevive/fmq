---
id: pubsub
title: PubSub
---

The example shows how to create one publisher and three subscribers with different subscription rules.

First of all, let's introduce a `Producer` that sends messages with a specific topic:

```scala mdoc:silent
import cats.effect.Async
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Stream
import io.fmq.frame.Frame
import io.fmq.socket.pubsub.Publisher

import scala.concurrent.duration._

class Producer[F[_]: Async](publisher: Publisher.Socket[F], topicA: String, topicB: String) {

  def generate: Stream[F, Unit] =
    Stream.repeatEval(sendA >> sendB >> Async[F].sleep(2000.millis))

  private def sendA: F[Unit] =
    publisher.sendFrame(Frame.Multipart(topicA, "We don't want to see this"))

  private def sendB: F[Unit] =
    publisher.sendFrame(Frame.Multipart(topicB, "We would like to see this"))

}
```

Then let's implement a consumer logic: 

```scala mdoc:silent
import cats.effect.Async
import cats.effect.syntax.async._
import cats.effect.std.Queue
import fs2.Stream
import io.fmq.frame.Frame
import io.fmq.socket.pubsub.Subscriber

import scala.concurrent.ExecutionContext

class Consumer[F[_]: Async](socket: Subscriber.Socket[F], blocker: ExecutionContext) {

  def consume: Stream[F, Frame[String]] = {
    def process(queue: Queue[F, Frame[String]]) =
      Stream.repeatEval(socket.receiveFrame[String]).evalMap(queue.offer).compile.drain

    for {
      queue  <- Stream.eval(Queue.unbounded[F, Frame[String]])
      _      <- Stream.resource(process(queue).backgroundOn(blocker))
      result <- Stream.repeatEval(queue.take)
    } yield result
  }

}
```

And the demo program that evaluates producer and subscribers in parallel:

```scala mdoc:silent
import cats.effect.{Resource, Sync}
import fs2.Stream
import io.fmq.Context
import io.fmq.socket.pubsub.Subscriber
import io.fmq.syntax.literals._

import scala.concurrent.ExecutionContext

class Demo[F[_]: Async](context: Context[F], blocker: ExecutionContext) {

  private def log(message: String): F[Unit] = Sync[F].delay(println(message))

  private val topicA = "my-topic-a"
  private val topicB = "my-topic-b"
  private val uri    = tcp_i"://localhost"

  private val appResource =
    for {
      pub    <- Resource.suspend(context.createPublisher.map(_.bindToRandomPort(uri)))
      addr   <- Resource.pure(pub.uri)
      subA   <- Resource.suspend(context.createSubscriber(Subscriber.Topic.utf8String(topicA)).map(_.connect(addr)))
      subB   <- Resource.suspend(context.createSubscriber(Subscriber.Topic.utf8String(topicB)).map(_.connect(addr)))
      subAll <- Resource.suspend(context.createSubscriber(Subscriber.Topic.All).map(_.connect(addr)))
    } yield (pub, subA, subB, subAll)

  val program: Stream[F, Unit] =
    Stream
      .resource(appResource)
      .flatMap {
        case (publisher, subscriberA, subscriberB, subscriberAll) =>
          val producer    = new Producer[F](publisher, topicA, topicB)
          val consumerA   = new Consumer[F](subscriberA, blocker)
          val consumerB   = new Consumer[F](subscriberB, blocker)
          val consumerAll = new Consumer[F](subscriberAll, blocker)

          Stream(
            producer.generate,
            consumerA.consume.evalMap(frame => log(s"ConsumerA. Received $frame")),
            consumerB.consume.evalMap(frame => log(s"ConsumerB. Received $frame")),
            consumerAll.consume.evalMap(frame => log(s"ConsumerAll. Received $frame"))
          ).parJoin(4)
      }

}
```

At the edge of our program we define our effect, `cats.effect.IO` in this case, and ask to evaluate the effects:

```scala mdoc:silent
import java.util.concurrent.Executors

import cats.effect.{IO, IOApp}
import cats.syntax.functor._
import io.fmq.Context

import scala.concurrent.ExecutionContext

object PubSub extends IOApp.Simple {

 override def run: IO[Unit] =
   blockingContext
     .flatMap(blocker => Context.create[IO](ioThreads = 1).tupleRight(blocker))
     .use { case (ctx, blocker) => new Demo[IO](ctx, blocker).program.compile.drain }

 private def blockingContext: Resource[IO, ExecutionContext] =
   Resource
     .make(IO.delay(Executors.newCachedThreadPool()))(e => IO.delay(e.shutdown()))
     .map(ExecutionContext.fromExecutor)
}
```

The output will be:
```text
ConsumerB. Received Multipart(my-topic-b, NonEmptyList(We would like to see this))
ConsumerAll. Received Multipart(my-topic-a, NonEmptyList(We don't want to see this))
ConsumerA. Received Multipart(my-topic-a, NonEmptyList(We don't want to see this))
ConsumerAll. Received Multipart(my-topic-b, NonEmptyList(We would like to see this))
```