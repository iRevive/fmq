package io.fmq
package socket
package pubsub

import cats.effect.{IO, Resource}
import cats.syntax.traverse._
import io.fmq.socket.SocketBehavior.SocketResource
import io.fmq.syntax.literals._
import org.scalatest.Assertion

import scala.concurrent.duration._

/**
  * Tests are using IO.sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
class SubscriberSpec extends IOSpec with SocketBehavior {

  "Subscriber" should {

    "filter multipart data" in withContext() { ctx: Context[IO] =>
      val uri   = tcp_i"://localhost"
      val topic = Subscriber.Topic.utf8String("B")

      def sendA(producer: ProducerSocket[IO]): IO[Unit] =
        producer.sendMore("A") >> producer.send("We don't want to see this")

      def sendB(producer: ProducerSocket[IO]): IO[Unit] =
        producer.sendMore("B") >> producer.send("We would like to see this")

      def create: Resource[IO, (ProducerSocket[IO], ConsumerSocket[IO])] =
        for {
          publisher  <- Resource.suspend(ctx.createPublisher.map(_.bindToRandomPort(uri)))
          subscriber <- Resource.suspend(ctx.createSubscriber(topic).map(_.connect(publisher.uri)))
        } yield (publisher, subscriber)

      def program(producer: ProducerSocket[IO], consumer: ConsumerSocket[IO]): IO[Assertion] =
        for {
          _        <- IO.sleep(200.millis)
          _        <- sendA(producer)
          _        <- sendB(producer)
          msg1     <- consumer.receive[String]
          hasMore1 <- consumer.hasReceiveMore
          msg2     <- consumer.receive[String]
          hasMore2 <- consumer.hasReceiveMore
        } yield {
          msg1 shouldBe "B"
          hasMore1 shouldBe true
          msg2 shouldBe "We would like to see this"
          hasMore2 shouldBe false
        }

      create.use((program _).tupled)
    }

    "subscribe to specific topic" in withRandomPortSocket(Subscriber.Topic.utf8String("my-topic")) { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      val messages = List("0", "my-topic-1", "1", "my-topic2", "my-topic-3")

      for {
        _      <- IO.sleep(200.millis)
        _      <- messages.traverse(producer.send[String])
        result <- collectMessages(consumer, 3L)
      } yield result shouldBe List("my-topic-1", "my-topic2", "my-topic-3")
    }

    "subscribe to specific topic (bytes)" in withRandomPortSocket(Subscriber.Topic.Bytes(Array(3, 1))) { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      val messages = List[Array[Byte]](Array(1), Array(2, 1, 3), Array(3, 1, 2), Array(3, 2, 1))

      for {
        _      <- IO.sleep(200.millis)
        _      <- messages.traverse(producer.send[Array[Byte]])
        result <- consumer.receive[Array[Byte]]
      } yield result shouldBe Array[Byte](3, 1, 2)
    }

    "subscribe to all topics" in withRandomPortSocket(Subscriber.Topic.All) { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      val messages = List("0", "my-topic-1", "1", "my-topic2", "my-topic-3")

      for {
        _      <- IO.sleep(200.millis)
        _      <- messages.traverse(producer.send[String])
        result <- collectMessages(consumer, messages.length.toLong)
      } yield result shouldBe messages
    }

  }

  def withRandomPortSocket[A](topic: Subscriber.Topic)(fa: SocketResource.Pair[IO] => IO[A]): A =
    withContext() { ctx: Context[IO] =>
      val uri = tcp_i"://localhost"

      (for {
        publisher  <- Resource.suspend(ctx.createPublisher.map(_.bindToRandomPort(uri)))
        subscriber <- Resource.suspend(ctx.createSubscriber(topic).map(_.connect(publisher.uri)))
      } yield SocketResource.Pair(publisher, subscriber)).use(fa)
    }

}
