---
id: pubsub
title: PubSub
---

The example shows how to create one publisher and three subscribers with different subscription rules.

First of all, let's introduce a `Producer` that sends messages with a specific topic:

```scala mdoc:silent
import cats.FlatMap
import cats.effect.Timer
import cats.syntax.flatMap._
import fs2.Stream
import io.fmq.socket.ProducerSocket

import scala.concurrent.duration._

class Producer[F[_]: FlatMap: Timer](publisher: ProducerSocket.TCP[F], topicA: String, topicB: String) {

  def generate: Stream[F, Unit] =
    Stream.repeatEval(sendA >> sendB >> Timer[F].sleep(2000.millis))

  private def sendA: F[Unit] =
    publisher.sendStringMore(topicA) >> publisher.sendString("We don't want to see this")

  private def sendB: F[Unit] =
    publisher.sendStringMore(topicB) >> publisher.sendString("We would like to see this")

}
```

Then let's implement a consumer logic: 

```scala mdoc:silent
import cats.effect.{Blocker, Concurrent, ContextShift, Resource}
import cats.effect.syntax.concurrent._
import fs2.Stream
import fs2.concurrent.Queue
import io.fmq.socket.ConsumerSocket

class Consumer[F[_]: Concurrent: ContextShift](socket: ConsumerSocket.TCP[F], blocker: Blocker) {

  def consume: Stream[F, List[String]] = {
    def process(queue: Queue[F, List[String]]) =
      blocker.blockOn(Stream.repeatEval(readBatch.compile.toList).through(queue.enqueue).compile.drain)

    for {
      queue  <- Stream.eval(Queue.unbounded[F, List[String]])
      _      <- Stream.resource(process(queue).background)
      result <- queue.dequeue
    } yield result
  }

  private def readBatch: Stream[F, String] =
    for {
      s <- Stream.eval(socket.recvString)
      r <- Stream.eval(socket.hasReceiveMore).ifM(Stream.emit(s) ++ readBatch, Stream.emit(s))
    } yield r

}
```

And the demo program that evaluates producer and subscribers in parallel:

```scala mdoc:silent
import cats.effect.{Concurrent, ContextShift, Resource, Sync, Timer}
import fs2.Stream
import io.fmq.Context
import io.fmq.address.{Address, Host, Uri}
import io.fmq.socket.Subscriber

class Demo[F[_]: Concurrent: ContextShift: Timer](context: Context[F], blocker: Blocker) {

  private def log(message: String): F[Unit] = Sync[F].delay(println(message))

  private val topicA = "my-topic-a"
  private val topicB = "my-topic-b"
  private val uri    = Uri.tcp(Address.HostOnly(Host.Fixed("localhost")))

  private val appResource =
    for {
      pub    <- context.createPublisher.flatMap(_.bindToRandomPort(uri))
      addr   <- Resource.pure(pub.uri)
      subA   <- context.createSubscriber(Subscriber.Topic.utf8String(topicA)).flatMap(_.connect(addr))
      subB   <- context.createSubscriber(Subscriber.Topic.utf8String(topicB)).flatMap(_.connect(addr))
      subAll <- context.createSubscriber(Subscriber.Topic.All).flatMap(_.connect(addr))
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
            consumerA.consume.evalMap(batch => log(s"ConsumerA. Received $batch")),
            consumerB.consume.evalMap(batch => log(s"ConsumerB. Received $batch")),
            consumerAll.consume.evalMap(batch => log(s"ConsumerAll. Received $batch"))
          ).parJoin(4)
      }

}
```

At the edge of out program we define our effect, `cats.effect.IO` in this case, and ask to evaluate the effects:
Define the entry point:

```scala mdoc:silent
import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.syntax.functor._
import io.fmq.Context

object PubSub extends IOApp {

 override def run(args: List[String]): IO[ExitCode] =
   Blocker[IO]
     .flatMap(blocker => Context.create[IO](ioThreads = 1, blocker).tupleRight(blocker))
     .use { case (ctx, blocker) => new Demo[IO](ctx, blocker).program.compile.drain.as(ExitCode.Success) }

}
```

The output will be:
```text
ConsumerA. Received List(my-topic-a, We don't want to see this)
ConsumerAll. Received List(my-topic-a, We don't want to see this)
ConsumerB. Received List(my-topic-b, We would like to see this)
ConsumerAll. Received List(my-topic-b, We would like to see this)
```