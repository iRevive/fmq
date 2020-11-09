package io.fmq.poll

import cats.data.{Kleisli, NonEmptyList}
import cats.effect.{IO, Resource}
import cats.syntax.flatMap._
import cats.syntax.apply._
import fs2.concurrent.Queue
import io.fmq.socket.pubsub.Subscriber
import io.fmq.socket.{ConsumerSocket, ProducerSocket}
import io.fmq.syntax.literals._
import io.fmq.{Context, IOSpec}
import org.scalatest.Assertion
import zmq.ZMQ
import zmq.poll.PollItem

import scala.concurrent.duration._

/**
  * Tests are using IO.sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
class PollerSpec extends IOSpec {

  "Poller[IO]" should {

    "zmq.ZMQ.poll behavior" in withContext() { ctx: Context[IO] =>
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
      ): IO[Assertion] = {
        def items =
          Array(
            new PollItem(consumerA.socket.base(), ZMQ.ZMQ_POLLIN),
            new PollItem(consumerB.socket.base(), ZMQ.ZMQ_POLLIN)
          )

        for {
          _       <- IO.sleep(200.millis)
          _       <- producer.send("Topic-A")
          events1 <- IO.delay(ZMQ.poll(poller.selector, items, -1))
          _       <- IO.sleep(100.millis)
          _       <- producer.send("Topic-B")
          _       <- IO.sleep(100.millis)
          events2 <- IO.delay(ZMQ.poll(poller.selector, items, -1))
          _       <- producer.send("Topic-A")
          _       <- producer.send("Topic-B")
          events3 <- IO.delay(ZMQ.poll(poller.selector, items, -1))
        } yield {
          events1 shouldBe 1
          events2 shouldBe 2
          events3 shouldBe 2
        }
      }

      create.use((program _).tupled)
    }

    "not call event handler if message is not available yet" in withContext() { ctx: Context[IO] =>
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
      ): IO[Assertion] =
        for {
          _      <- IO.sleep(200.millis)
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
          _                  <- IO.sleep(100.millis)
          _                  <- poller.poll(items, PollTimeout.Infinity)
          (queueA3, queueB3) <- (queueA.tryDequeue1, queueB.tryDequeue1).tupled
          _                  <- producer.send("Topic-A")
          _                  <- producer.send("Topic-B")
          _                  <- IO.sleep(100.millis)
          _                  <- poller.poll(items, PollTimeout.Infinity)
          (queueA4, queueB4) <- (queueA.tryDequeue1, queueB.tryDequeue1).tupled
        } yield {
          queueA1 shouldBe empty
          queueB1 shouldBe empty

          queueA2 shouldBe Some("Topic-A")
          queueB2 shouldBe empty

          queueA3 shouldBe empty
          queueB3 shouldBe Some("Topic-B")

          queueA4 shouldBe Some("Topic-A")
          queueB4 shouldBe Some("Topic-B")
        }

      create.use((program _).tupled)
    }

    "read from multiple sockets" in withContext(15.seconds) { ctx: Context[IO] =>
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
      ): IO[Assertion] = {
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
            _  <- IO.sleep(200.millis)
            a1 <- queueA.dequeue1
            a2 <- queueA.dequeue1
            b1 <- queueB.dequeue1
            b2 <- queueB.dequeue1
          } yield {
            List(a1, a2) shouldBe List("Topic-A", "Topic-A")
            List(b1, b2) shouldBe List("Topic-B", "Topic-B")
          }
        }
      }

      create.use((program _).tupled)
    }

  }

}
