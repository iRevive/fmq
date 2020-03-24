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
import cats.syntax.functor._
import fs2.Stream
import io.fmq.frame.Frame
import io.fmq.socket.ProducerSocket

import scala.concurrent.duration._

class Producer[F[_]: FlatMap: Timer](publisher: ProducerSocket[F], topicA: String, topicB: String) {

  def generate: Stream[F, Unit] =
    Stream.repeatEval(sendA >> sendB >> Timer[F].sleep(2000.millis))

  private def sendA: F[Unit] =
    publisher.sendFrame(Frame.Multipart(topicA, "We don't want to see this"))

  private def sendB: F[Unit] =
    publisher.sendFrame(Frame.Multipart(topicB, "We would like to see this"))

}
```

Then let's implement a consumer logic: 

```scala mdoc:silent
import cats.effect.{Blocker, Concurrent, ContextShift}
import cats.effect.syntax.concurrent._
import fs2.Stream
import fs2.concurrent.Queue
import io.fmq.frame.Frame
import io.fmq.socket.ConsumerSocket

class Consumer[F[_]: Concurrent: ContextShift](socket: ConsumerSocket[F], blocker: Blocker) {

  def consume: Stream[F, Frame[String]] = {
    def process(queue: Queue[F, Frame[String]]) =
      blocker.blockOn(Stream.repeatEval(socket.receiveFrame[String]).through(queue.enqueue).compile.drain)

    for {
      queue  <- Stream.eval(Queue.unbounded[F, Frame[String]])
      _      <- Stream.resource(process(queue).background)
      result <- queue.dequeue
    } yield result
  }

}
```

And the demo program that evaluates producer and subscribers in parallel:

```scala mdoc:silent
import cats.effect.{Concurrent, ContextShift, Resource, Sync, Timer}
import fs2.Stream
import io.fmq.Context
import io.fmq.address.{Address, Host, Uri}
import io.fmq.socket.pubsub.Subscriber

class Demo[F[_]: Concurrent: ContextShift: Timer](context: Context[F], blocker: Blocker) {

  private def log(message: String): F[Unit] = Sync[F].delay(println(message))

  private val topicA = "my-topic-a"
  private val topicB = "my-topic-b"
  private val uri    = Uri.Incomplete.TCP(Address.HostOnly(Host.Fixed("localhost")))

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
ConsumerB. Received Multipart(my-topic-b, NonEmptyList(We would like to see this))
ConsumerAll. Received Multipart(my-topic-a, NonEmptyList(We don't want to see this))
ConsumerA. Received Multipart(my-topic-a, NonEmptyList(We don't want to see this))
ConsumerAll. Received Multipart(my-topic-b, NonEmptyList(We would like to see this))
```