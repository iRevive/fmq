package io.fmq.poll

import cats.data.{Kleisli, NonEmptyList}
import cats.effect.{IO, Resource, Timer}
import cats.syntax.apply._
import cats.syntax.flatMap._
import fs2.concurrent.Queue
import io.fmq.IOSpec
import io.fmq.socket.pubsub.Subscriber
import io.fmq.socket.{ConsumerSocket, ProducerSocket}
import io.fmq.syntax.literals._
import weaver.Expectations
import zmq.ZMQ
import zmq.poll.PollItem

import scala.concurrent.duration._

/**
  * Tests are using Timer[IO].sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
object PollerSpec extends IOSpec {

  test("zmq.ZMQ.poll behavior") { ctx =>
    val topicA = Subscriber.Topic.utf8String("Topic-A")
    val topicB = Subscriber.Topic.utf8String("Topic-B")
    val uri    = tcp_i"://localhost"

    def create: Resource[IO, (ProducerSocket[IO], ConsumerSocket[IO], ConsumerSocket[IO], Poller[IO])] =
      for {
        publisher <- Resource.suspend(ctx.createPublisher.map(_.bindToRandomPort(uri)))
        consumerA <- Resource.suspend(ctx.createSubscriber(topicA).map(_.connect(publisher.uri)))
        consumerB <- Resource.suspend(ctx.createSubscriber(topicB).map(_.connect(publisher.uri)))
        poller    <- ctx.createPoller
      } yield (publisher, consumerA, consumerB, poller)

    def program(
        producer: ProducerSocket[IO],
        consumerA: ConsumerSocket[IO],
        consumerB: ConsumerSocket[IO],
        poller: Poller[IO]
    ): IO[Expectations] = {
      def items =
        Array(
          new PollItem(consumerA.socket.base(), ZMQ.ZMQ_POLLIN),
          new PollItem(consumerB.socket.base(), ZMQ.ZMQ_POLLIN)
        )

      for {
        _       <- Timer[IO].sleep(200.millis)
        _       <- producer.send("Topic-A")
        events1 <- IO.delay(ZMQ.poll(poller.selector, items, -1))
        _       <- Timer[IO].sleep(100.millis)
        _       <- producer.send("Topic-B")
        _       <- Timer[IO].sleep(100.millis)
        events2 <- IO.delay(ZMQ.poll(poller.selector, items, -1))
        _       <- producer.send("Topic-A")
        _       <- producer.send("Topic-B")
        events3 <- IO.delay(ZMQ.poll(poller.selector, items, -1))
      } yield expect(events1 == 1) and expect(events2 == 2) and expect(events3 == 2)
    }

    create.use((program _).tupled)
  }

  test("not call event handler if message is not available yet") { ctx =>
    val topicA = Subscriber.Topic.utf8String("Topic-A")
    val topicB = Subscriber.Topic.utf8String("Topic-B")
    val uri    = tcp_i"://localhost"

    def create: Resource[IO, (ProducerSocket[IO], ConsumerSocket[IO], ConsumerSocket[IO], Poller[IO])] =
      for {
        publisher <- Resource.suspend(ctx.createPublisher.map(_.bindToRandomPort(uri)))
        consumerA <- Resource.suspend(ctx.createSubscriber(topicA).map(_.connect(publisher.uri)))
        consumerB <- Resource.suspend(ctx.createSubscriber(topicB).map(_.connect(publisher.uri)))
        poller    <- ctx.createPoller
      } yield (publisher, consumerA, consumerB, poller)

    def handler(queue: Queue[IO, String]): ConsumerHandler[IO] =
      Kleisli(socket => socket.receive[String] >>= queue.enqueue1)

    def program(
        producer: ProducerSocket[IO],
        consumerA: ConsumerSocket[IO],
        consumerB: ConsumerSocket[IO],
        poller: Poller[IO]
    ): IO[Expectations] =
      for {
        _      <- Timer[IO].sleep(200.millis)
        queueA <- Queue.unbounded[IO, String]
        queueB <- Queue.unbounded[IO, String]
        items = NonEmptyList.of(
          PollEntry.Read(consumerA, handler(queueA)),
          PollEntry.Read(consumerB, handler(queueB))
        )
        _                  <- poller.poll(items, PollTimeout.Fixed(200.millis))
        (queueA1, queueB1) <- (queueA.tryDequeue1, queueB.tryDequeue1).tupled
        _                  <- producer.send("Topic-A")
        _                  <- poller.poll(items, PollTimeout.Infinity)
        (queueA2, queueB2) <- (queueA.tryDequeue1, queueB.tryDequeue1).tupled
        _                  <- producer.send("Topic-B")
        _                  <- Timer[IO].sleep(100.millis)
        _                  <- poller.poll(items, PollTimeout.Infinity)
        (queueA3, queueB3) <- (queueA.tryDequeue1, queueB.tryDequeue1).tupled
        _                  <- producer.send("Topic-A")
        _                  <- producer.send("Topic-B")
        _                  <- Timer[IO].sleep(100.millis)
        _                  <- poller.poll(items, PollTimeout.Infinity)
        (queueA4, queueB4) <- (queueA.tryDequeue1, queueB.tryDequeue1).tupled
      } yield expect(queueA1.isEmpty) and
        expect(queueB1.isEmpty) and
        expect(queueA2.contains("Topic-A")) and
        expect(queueB2.isEmpty) and
        expect(queueA3.isEmpty) and
        expect(queueB3.contains("Topic-B")) and
        expect(queueA4.contains("Topic-A")) and
        expect(queueB4.contains("Topic-B"))

    create.use((program _).tupled)
  }

  test("read from multiple sockets") { ctx =>
    val topicA = Subscriber.Topic.utf8String("Topic-A")
    val topicB = Subscriber.Topic.utf8String("Topic-B")
    val uri    = tcp_i"://localhost"

    def create: Resource[IO, (ProducerSocket[IO], ConsumerSocket[IO], ConsumerSocket[IO], Poller[IO])] =
      for {
        publisher <- Resource.suspend(ctx.createPublisher.map(_.bindToRandomPort(uri)))
        consumerA <- Resource.suspend(ctx.createSubscriber(topicA).map(_.connect(publisher.uri)))
        consumerB <- Resource.suspend(ctx.createSubscriber(topicB).map(_.connect(publisher.uri)))
        poller    <- ctx.createPoller
      } yield (publisher, consumerA, consumerB, poller)

    def consumerHandler(queue: Queue[IO, String]): ConsumerHandler[IO] =
      Kleisli(socket => socket.receive[String] >>= queue.enqueue1)

    def producerHandler: ProducerHandler[IO] =
      Kleisli(socket => socket.send("Topic-A") >> socket.send("Topic-B"))

    def program(
        producer: ProducerSocket[IO],
        consumerA: ConsumerSocket[IO],
        consumerB: ConsumerSocket[IO],
        poller: Poller[IO]
    ): IO[Expectations] = {
      val setup: Resource[IO, (Queue[IO, String], Queue[IO, String])] =
        for {
          queueA <- Resource.liftF(Queue.unbounded[IO, String])
          queueB <- Resource.liftF(Queue.unbounded[IO, String])
          items = NonEmptyList.of(
            PollEntry.Write(producer, producerHandler),
            PollEntry.Read(consumerA, consumerHandler(queueA)),
            PollEntry.Read(consumerB, consumerHandler(queueB))
          )
          _ <- poller.poll(items, PollTimeout.Infinity).foreverM.background
        } yield (queueA, queueB)

      setup.use { pair =>
        val (queueA, queueB) = pair

        for {
          _  <- Timer[IO].sleep(200.millis)
          a1 <- queueA.dequeue1
          a2 <- queueA.dequeue1
          b1 <- queueB.dequeue1
          b2 <- queueB.dequeue1
        } yield expect(List(a1, a2) == List("Topic-A", "Topic-A")) and expect(List(b1, b2) == List("Topic-B", "Topic-B"))
      }
    }

    create.use((program _).tupled)
  }

}
