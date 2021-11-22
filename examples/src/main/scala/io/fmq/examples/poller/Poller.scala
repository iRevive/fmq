package io.fmq.examples.poller

import java.util.concurrent.Executors

import cats.data.{Kleisli, NonEmptyList}
import cats.effect.std.Queue
import cats.effect.syntax.async._
import cats.effect._
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Stream
import io.fmq.Context
import io.fmq.frame.Frame
import io.fmq.poll.{ConsumerHandler, PollEntry, PollTimeout}
import io.fmq.socket.ProducerSocket
import io.fmq.socket.pubsub.Subscriber
import io.fmq.syntax.literals._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object PollerApp extends IOApp.Simple {

  override def run: IO[Unit] =
    blockingContext
      .flatMap(blocker => Context.create[IO](ioThreads = 1).tupleRight(blocker))
      .use { case (ctx, blocker) => new PollerDemo[IO](ctx, blocker).program.compile.drain }

  private def blockingContext: Resource[IO, ExecutionContext] =
    Resource
      .make(IO.delay(Executors.newSingleThreadExecutor()))(e => IO.delay(e.shutdown()))
      .map(ExecutionContext.fromExecutor)

}

class PollerDemo[F[_]: Async](context: Context[F], blocker: ExecutionContext) {

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
      .flatMap { case (publisher, subscriberA, subscriberB, subscriberAll, poller) =>
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

class Producer[F[_]: Async](publisher: ProducerSocket[F], topicA: String, topicB: String) {

  def generate: Stream[F, Unit] =
    Stream.repeatEval(sendA >> sendB >> Async[F].sleep(2000.millis))

  private def sendA: F[Unit] =
    publisher.sendMultipart(Frame.Multipart(topicA, "We don't want to see this"))

  private def sendB: F[Unit] =
    publisher.sendMultipart(Frame.Multipart(topicB, "We would like to see this"))

}
