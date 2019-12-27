package io.fmq.poll

import cats.data.Kleisli
import cats.effect.{IO, Resource, Timer}
import cats.syntax.flatMap._
import cats.syntax.apply._
import fs2.concurrent.Queue
import io.fmq.domain.{Protocol, SubscribeTopic}
import io.fmq.socket.{ConsumerSocket, ProducerSocket, SocketBehavior}
import io.fmq.{Context, IOSpec}
import org.scalatest.Assertion

import scala.concurrent.duration._

/**
  * Tests are using Timer[IO].sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
class PollerSpec extends IOSpec with SocketBehavior {

  "Poller[IO]" should {

    "not call event handler if message is not available yet" in withContext() { ctx: Context[IO] =>
      val timeout = PollTimeout.Fixed(200.millis)

      val topicA = SubscribeTopic.utf8String("Topic-A")
      val topicB = SubscribeTopic.utf8String("Topic-B")

      def create: Resource[IO, (ProducerSocket[IO], ConsumerSocket[IO], ConsumerSocket[IO], Poller[IO])] =
        for {
          pub       <- ctx.createPublisher
          subA      <- ctx.createSubscriber(topicA)
          subB      <- ctx.createSubscriber(topicB)
          publisher <- pub.bindToRandomPort(Protocol.tcp("localhost"))
          consumerA <- subA.connect(Protocol.tcp("localhost", publisher.port))
          consumerB <- subB.connect(Protocol.tcp("localhost", publisher.port))
          poller    <- ctx.createPoller
        } yield (publisher, consumerA, consumerB, poller)

      def handler(queue: Queue[IO, String]): ConsumerHandler[IO] =
        Kleisli(socket => socket.recvString >>= queue.enqueue1)

      def program(
          producer: ProducerSocket[IO],
          consumerA: ConsumerSocket[IO],
          consumerB: ConsumerSocket[IO],
          poller: Poller[IO]
      ): IO[Assertion] =
        for {
          _                  <- Timer[IO].sleep(200.millis)
          queueA             <- Queue.unbounded[IO, String]
          queueB             <- Queue.unbounded[IO, String]
          _                  <- poller.registerConsumer(consumerA, handler(queueA))
          _                  <- poller.registerConsumer(consumerB, handler(queueB))
          _                  <- poller.poll(timeout)
          (queueA1, queueB1) <- (queueA.tryDequeue1, queueB.tryDequeue1).tupled
          _                  <- producer.sendString("Topic-A")
          _                  <- poller.poll(timeout)
          (queueA2, queueB2) <- (queueA.tryDequeue1, queueB.tryDequeue1).tupled
          _                  <- producer.sendString("Topic-B")
          _                  <- Timer[IO].sleep(100.millis)
          _                  <- poller.poll(timeout)
          (queueA3, queueB3) <- (queueA.tryDequeue1, queueB.tryDequeue1).tupled
          _                  <- producer.sendString("Topic-A")
          _                  <- producer.sendString("Topic-B")
          _                  <- Timer[IO].sleep(100.millis)
          _                  <- poller.poll(timeout)
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
      val topicA = SubscribeTopic.utf8String("Topic-A")
      val topicB = SubscribeTopic.utf8String("Topic-B")

      def create: Resource[IO, (ProducerSocket[IO], ConsumerSocket[IO], ConsumerSocket[IO], Poller[IO])] =
        for {
          pub       <- ctx.createPublisher
          subA      <- ctx.createSubscriber(topicA)
          subB      <- ctx.createSubscriber(topicB)
          publisher <- pub.bindToRandomPort(Protocol.tcp("localhost"))
          consumerA <- subA.connect(Protocol.tcp("localhost", publisher.port))
          consumerB <- subB.connect(Protocol.tcp("localhost", publisher.port))
          poller    <- ctx.createPoller
        } yield (publisher, consumerA, consumerB, poller)

      def consumerHandler(queue: Queue[IO, String]): ConsumerHandler[IO] =
        Kleisli(socket => socket.recvString >>= queue.enqueue1)

      def producerHandler: ProducerHandler[IO] =
        Kleisli(socket => socket.sendString("Topic-A") >> socket.sendString("Topic-B"))

      def program(
          producer: ProducerSocket[IO],
          consumerA: ConsumerSocket[IO],
          consumerB: ConsumerSocket[IO],
          poller: Poller[IO]
      ): IO[Assertion] = {
        val poll = poller.poll(PollTimeout.Infinity).foreverM

        Resource.make(poll.start)(_.cancel).use { _ =>
          for {
            _      <- Timer[IO].sleep(200.millis)
            queueA <- Queue.unbounded[IO, String]
            queueB <- Queue.unbounded[IO, String]
            _      <- poller.registerProducer(producer, producerHandler)
            _      <- poller.registerConsumer(consumerA, consumerHandler(queueA))
            _      <- poller.registerConsumer(consumerB, consumerHandler(queueB))
            a1     <- queueA.dequeue1
            a2     <- queueA.dequeue1
            b1     <- queueB.dequeue1
            b2     <- queueB.dequeue1
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
