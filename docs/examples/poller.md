---
id: poller
title: Poller
---

The `Poller` provides a way to invoke a handler once a socket can either receive or send a message.  
The `ConsumerHandler` is a simple `Kleisli`: `Kleisli[F, ConsumerSocket[F], Unit]`.   

The best way to use the poller is enqueue messages into the queue: 
```scala
def handler(queue: Queue[F, String]): ConsumerHandler[F] =
  Kleisli(socket => socket.receive[String] >>= queue.offer)
```

Then the `poller.poll` operation can be evaluated on the blocking context:
```scala
poller.poll(pollItems, PollTimeout.Infinity).foreverM.evalOn(blocker)
```

Thus all consuming operations is being executed on the one blocking thread, while the processing can be performed on the general context.  

## Example

The example shows how to use poller with three subscribers.

First of all, let's introduce a `Producer` that sends messages with a specific topic:

```scala mdoc:silent
import cats.effect.Async
import cats.syntax.flatMap._
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

And the demo program that evaluates producer and subscribers in parallel:

```scala mdoc:silent
import cats.data.{Kleisli, NonEmptyList}
import cats.effect.{Async, Resource}
import cats.effect.syntax.async._
import cats.effect.std.Queue
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Stream
import io.fmq.Context
import io.fmq.poll.{ConsumerHandler, PollEntry, PollTimeout}
import io.fmq.socket.pubsub.Subscriber
import io.fmq.syntax.literals._

import scala.concurrent.ExecutionContext

class Demo[F[_]: Async](context: Context[F], blocker: ExecutionContext) {

  private def log(message: String): F[Unit] = Async[F].delay(println(message))

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
      poller <- context.createPoller
    } yield (pub, subA, subB, subAll, poller)

  val program: Stream[F, Unit] =
    Stream
      .resource(appResource)
      .flatMap {
        case (publisher, subscriberA, subscriberB, subscriberAll, poller) =>
          val producer = new Producer[F](publisher, topicA, topicB)

          def handler(queue: Queue[F, String]): ConsumerHandler[F] =
            Kleisli(socket => socket.receive[String] >>= queue.offer)
          
          // evaluates poll on a blocking context
          def poll(queueA: Queue[F, String], queueB: Queue[F, String], queueAll: Queue[F, String]): F[Unit] = {
            val items = NonEmptyList.of(
              PollEntry.Read(subscriberA, handler(queueA)), 
              PollEntry.Read(subscriberB, handler(queueB)), 
              PollEntry.Read(subscriberAll, handler(queueAll))
            )

            poller.poll(items, PollTimeout.Infinity).foreverM[Unit].evalOn(blocker)
          }
          
          for {
            queueA   <- Stream.eval(Queue.unbounded[F, String])
            queueB   <- Stream.eval(Queue.unbounded[F, String])
            queueAll <- Stream.eval(Queue.unbounded[F, String])
            _ <- Stream(
              producer.generate,
              Stream.eval(poll(queueA, queueB, queueAll)),
              Stream.repeatEval(queueA.take).evalMap(frame => log(s"ConsumerA. Received $frame")),
              Stream.repeatEval(queueB.take).evalMap(frame => log(s"ConsumerB. Received $frame")),
              Stream.repeatEval(queueAll.take).evalMap(frame => log(s"ConsumerAll. Received $frame"))
            ).parJoinUnbounded
          } yield ()
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

object Poller extends IOApp.Simple {

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
ConsumerB. Received [my-topic-b]
ConsumerA. Received [my-topic-a]
ConsumerAll. Received [my-topic-a]
ConsumerB. Received [We would like to see this]
ConsumerA. Received [We don't want to see this]
ConsumerAll. Received [We don't want to see this]
ConsumerAll. Received [my-topic-b]
ConsumerAll. Received [We would like to see this]
```