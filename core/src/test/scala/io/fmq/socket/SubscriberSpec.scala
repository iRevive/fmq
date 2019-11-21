package io.fmq
package socket

import cats.effect.{IO, Resource, Timer}
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.traverse._
import io.fmq.domain.{Protocol, SubscribeTopic}
import io.fmq.socket.SocketBehavior.SocketResource
import org.scalatest.Assertion

import scala.concurrent.duration._

/**
  * Tests are using Timer[IO].sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
class SubscriberSpec extends IOSpec with SocketBehavior {

  "Subscriber" should {

    "filter multipart data" in withContext() { ctx: Context[IO] =>
      val protocol = Protocol.tcp("localhost")
      val topic    = SubscribeTopic.utf8String("B")

      def sendA(producer: ProducerSocket[IO]): IO[Unit] =
        producer.sendUtf8StringMore("A") >> producer.sendUtf8String("We don't want to see this")

      def sendB(producer: ProducerSocket[IO]): IO[Unit] =
        producer.sendUtf8StringMore("B") >> producer.sendUtf8String("We would like to see this")

      def create: Resource[IO, (ProducerSocket[IO], ConsumerSocket[IO])] =
        for {
          pub        <- ctx.createPublisher[IO]
          publisher  <- pub.bindToRandomPort(protocol)
          sub        <- ctx.createSubscriber[IO](topic)
          subscriber <- sub.connect(Protocol.tcp("localhost", publisher.port))
        } yield (publisher, subscriber)

      def program(producer: ProducerSocket[IO], consumer: ConsumerSocket[IO]): IO[Assertion] =
        for {
          _        <- Timer[IO].sleep(200.millis)
          _        <- sendA(producer)
          _        <- sendB(producer)
          msg1     <- consumer.recvString
          hasMore1 <- consumer.hasReceiveMore
          msg2     <- consumer.recvString
          hasMore2 <- consumer.hasReceiveMore
        } yield {
          msg1 shouldBe "B"
          hasMore1 shouldBe true
          msg2 shouldBe "We would like to see this"
          hasMore2 shouldBe false
        }

      create.use((program _).tupled)
    }

    "subscribe to specific topic" in withRandomPortSocket(SubscribeTopic.utf8String("my-topic")) { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      val messages = List("0", "my-topic-1", "1", "my-topic2", "my-topic-3")

      for {
        _      <- Timer[IO].sleep(200.millis)
        _      <- messages.traverse(producer.sendUtf8String)
        result <- collectMessages(consumer, 3L)
      } yield result shouldBe List("my-topic-1", "my-topic2", "my-topic-3")
    }

    "subscribe to specific topic (bytes)" in withRandomPortSocket(SubscribeTopic.Bytes(Array(3, 1))) { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      val messages = List[Array[Byte]](Array(1), Array(2, 1, 3), Array(3, 1, 2), Array(3, 2, 1))

      for {
        _      <- Timer[IO].sleep(200.millis)
        _      <- messages.traverse(producer.send)
        result <- consumer.recv
      } yield result shouldBe Array[Byte](3, 1, 2)
    }

    "subscribe to all topics" in withRandomPortSocket(SubscribeTopic.All) { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      val messages = List("0", "my-topic-1", "1", "my-topic2", "my-topic-3")

      for {
        _      <- Timer[IO].sleep(200.millis)
        _      <- messages.traverse(producer.sendUtf8String)
        result <- collectMessages(consumer, messages.length.toLong)
      } yield result shouldBe messages
    }

  }

  def withRandomPortSocket[A](topic: SubscribeTopic)(fa: SocketResource.Pair[IO] => IO[A]): A =
    withContext() { ctx: Context[IO] =>
      (for {
        pub      <- ctx.createPublisher[IO]
        sub      <- ctx.createSubscriber[IO](topic)
        producer <- pub.bindToRandomPort(Protocol.tcp("localhost"))
        consumer <- sub.connect(Protocol.tcp("localhost", producer.port))
      } yield SocketResource.Pair(producer, consumer)).use(fa)
    }

}
