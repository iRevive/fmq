---
id: poller
title: Poller
---

The `Poller` provides a way to invoke a handler once a socket can either receive or send a message.  
The `ConsumerHandler` is a simple `Kleisli`: `Kleisli[F, ConsumerSocket[F], Unit]`.   
The best way to use the poller is enqueue messages into the queue: 
```scala
def handler(queue: Queue[F, String]): ConsumerHandler[F] =
  Kleisli(socket => socket.recvString >>= queue.enqueue1)
```

Then the `poller.poll` operation can be evaluated on the blocking context:
```scala
blocker.blockOn(poller.poll(PollTimeout.Infinity).foreverM)
```

Thus all consuming operations is being executed on the one blocking thread, while the processing can be performed on the general context.  



## Example

The example shows how to use poller with three subscribers.

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

And the demo program that evaluates producer and subscribers in parallel:

```scala mdoc:silent
import cats.data.Kleisli
import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Sync, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Stream
import fs2.concurrent.Queue
import io.fmq.Context
import io.fmq.address.{Address, Host, Protocol, Uri}
import io.fmq.poll.{ConsumerHandler, PollTimeout}
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
      poller <- context.createPoller
    } yield (pub, subA, subB, subAll, poller)

  val program: Stream[F, Unit] =
    Stream
      .resource(appResource)
      .flatMap {
        case (publisher, subscriberA, subscriberB, subscriberAll, poller) =>
          val producer = new Producer[F](publisher, topicA, topicB)

          def handler(queue: Queue[F, String]): ConsumerHandler[F, Protocol.TCP, Address.Full] =
            Kleisli(socket => socket.recvString >>= queue.enqueue1)
          
          def configurePoller(queueA: Queue[F, String], queueB: Queue[F, String], queueAll: Queue[F, String]): F[Unit] =
            for {
              _ <- poller.registerConsumer(subscriberA, handler(queueA))
              _ <- poller.registerConsumer(subscriberB, handler(queueB))
              _ <- poller.registerConsumer(subscriberAll, handler(queueAll))
            } yield ()
          
          // evaluates poll on a blocking context
          val poll = blocker.blockOn(poller.poll(PollTimeout.Infinity).foreverM[Unit])
          
          for {
            queueA   <- Stream.eval(Queue.unbounded[F, String])
            queueB   <- Stream.eval(Queue.unbounded[F, String])
            queueAll <- Stream.eval(Queue.unbounded[F, String])
            _        <- Stream.eval(configurePoller(queueA, queueB, queueAll))
            _ <- Stream(
              producer.generate,
              Stream.eval(poll),
              queueA.dequeue.evalMap(frame => log(s"ConsumerA. Received $frame")),
              queueB.dequeue.evalMap(frame => log(s"ConsumerB. Received $frame")),
              queueAll.dequeue.evalMap(frame => log(s"ConsumerAll. Received $frame"))
            ).parJoinUnbounded
          } yield ()
      }

}
```

At the edge of out program we define our effect, `cats.effect.IO` in this case, and ask to evaluate the effects:
Define the entry point:

```scala mdoc:silent
import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.syntax.functor._
import io.fmq.Context

object Poller extends IOApp {

 override def run(args: List[String]): IO[ExitCode] =
   Blocker[IO]
     .flatMap(blocker => Context.create[IO](ioThreads = 1, blocker).tupleRight(blocker))
     .use { case (ctx, blocker) => new Demo[IO](ctx, blocker).program.compile.drain.as(ExitCode.Success) }

}
```

The output will be:
```text
ConsumerB. Received [my-topic-b]
ConsumerA. Received [my-topic-a]
ConsumerAll. Received [my-topic-a]
ConsumerB. Received [We would like to see this]
ConsumerA. Received [We don't want to see this]
ConsumerAll. Received [We don't want to see this]
ConsumerAll. Received [my-topic-b]
ConsumerAll. Received [We would like to see this]
```