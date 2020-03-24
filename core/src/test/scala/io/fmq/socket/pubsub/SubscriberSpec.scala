package io.fmq
package socket
package pubsub

import cats.effect.{IO, Resource, Timer}
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.traverse._
import io.fmq.address.{Address, Host, Protocol, Uri}
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
      val uri   = Uri.tcp(Address.HostOnly(Host.Fixed("localhost")))
      val topic = Subscriber.Topic.utf8String("B")

      def sendA(producer: ProducerSocket.TCP[IO]): IO[Unit] =
        producer.sendStringMore("A") >> producer.sendString("We don't want to see this")

      def sendB(producer: ProducerSocket.TCP[IO]): IO[Unit] =
        producer.sendStringMore("B") >> producer.sendString("We would like to see this")

      def create: Resource[IO, (ProducerSocket.TCP[IO], ConsumerSocket.TCP[IO])] =
        for {
          pub        <- ctx.createPublisher
          publisher  <- pub.bindToRandomPort(uri)
          sub        <- ctx.createSubscriber(topic)
          subscriber <- sub.connect(publisher.uri)
        } yield (publisher, subscriber)

      def program(producer: ProducerSocket.TCP[IO], consumer: ConsumerSocket.TCP[IO]): IO[Assertion] =
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

    "subscribe to specific topic" in withRandomPortSocket(Subscriber.Topic.utf8String("my-topic")) { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      val messages = List("0", "my-topic-1", "1", "my-topic2", "my-topic-3")

      for {
        _      <- Timer[IO].sleep(200.millis)
        _      <- messages.traverse(producer.sendString)
        result <- collectMessages(consumer, 3L)
      } yield result shouldBe List("my-topic-1", "my-topic2", "my-topic-3")
    }

    "subscribe to specific topic (bytes)" in withRandomPortSocket(Subscriber.Topic.Bytes(Array(3, 1))) { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      val messages = List[Array[Byte]](Array(1), Array(2, 1, 3), Array(3, 1, 2), Array(3, 2, 1))

      for {
        _      <- Timer[IO].sleep(200.millis)
        _      <- messages.traverse(producer.send)
        result <- consumer.recv
      } yield result shouldBe Array[Byte](3, 1, 2)
    }

    "subscribe to all topics" in withRandomPortSocket(Subscriber.Topic.All) { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      val messages = List("0", "my-topic-1", "1", "my-topic2", "my-topic-3")

      for {
        _      <- Timer[IO].sleep(200.millis)
        _      <- messages.traverse(producer.sendString)
        result <- collectMessages(consumer, messages.length.toLong)
      } yield result shouldBe messages
    }

  }

  def withRandomPortSocket[A](topic: Subscriber.Topic)(fa: SocketResource.Pair[IO, Protocol.TCP, Address.Full] => IO[A]): A =
    withContext() { ctx: Context[IO] =>
      val uri = Uri.tcp(Address.HostOnly(Host.Fixed("localhost")))

      (for {
        pub      <- ctx.createPublisher
        sub      <- ctx.createSubscriber(topic)
        producer <- pub.bindToRandomPort(uri)
        consumer <- sub.connect(producer.uri)
      } yield SocketResource.Pair(producer, consumer)).use(fa)
    }

}
