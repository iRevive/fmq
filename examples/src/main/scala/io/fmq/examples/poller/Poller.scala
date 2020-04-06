package io.fmq.examples.poller

import cats.FlatMap
import cats.data.Kleisli
import cats.effect.{Blocker, Concurrent, ContextShift, ExitCode, IO, IOApp, Resource, Sync, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Stream
import fs2.concurrent.Queue
import io.fmq.Context
import io.fmq.frame.Frame
import io.fmq.poll.{ConsumerHandler, PollTimeout}
import io.fmq.socket.ProducerSocket
import io.fmq.socket.pubsub.Subscriber
import io.fmq.syntax.literals._

import scala.concurrent.duration._

object PollerApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    Blocker[IO]
      .flatMap(blocker => Context.create[IO](ioThreads = 1, blocker).tupleRight(blocker))
      .use { case (ctx, blocker) => new PollerDemo[IO](ctx, blocker).program.compile.drain.as(ExitCode.Success) }

}

class PollerDemo[F[_]: Concurrent: ContextShift: Timer](context: Context[F], blocker: Blocker) {

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
      poller <- context.createPoller
    } yield (pub, subA, subB, subAll, poller)

  val program: Stream[F, Unit] =
    Stream
      .resource(appResource)
      .flatMap {
        case (publisher, subscriberA, subscriberB, subscriberAll, poller) =>
          val producer = new Producer[F](publisher, topicA, topicB)

          def handler(queue: Queue[F, String]): ConsumerHandler[F] =
            Kleisli(socket => socket.receive[String] >>= queue.enqueue1)

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

class Producer[F[_]: FlatMap: Timer](publisher: ProducerSocket[F], topicA: String, topicB: String) {

  def generate: Stream[F, Unit] =
    Stream.repeatEval(sendA >> sendB >> Timer[F].sleep(2000.millis))

  private def sendA: F[Unit] =
    publisher.sendMultipart(Frame.Multipart(topicA, "We don't want to see this"))

  private def sendB: F[Unit] =
    publisher.sendMultipart(Frame.Multipart(topicB, "We would like to see this"))

}
