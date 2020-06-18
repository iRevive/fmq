package io.fmq
package socket
package pubsub

import cats.effect.{IO, Resource, Timer}
import cats.syntax.flatMap._
import cats.syntax.traverse._
import io.fmq.socket.SocketBehavior.SocketResource
import io.fmq.syntax.literals._
import weaver.Expectations

import scala.concurrent.duration._

/**
  * Tests are using Timer[IO].sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
object SubscriberSpec extends IOSpec with SocketBehavior {

  test("filter multipart data") { ctx: Context[IO] =>
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

    def program(producer: ProducerSocket[IO], consumer: ConsumerSocket[IO]): IO[Expectations] =
      for {
        _        <- Timer[IO].sleep(200.millis)
        _        <- sendA(producer)
        _        <- sendB(producer)
        msg1     <- consumer.receive[String]
        hasMore1 <- consumer.hasReceiveMore
        msg2     <- consumer.receive[String]
        hasMore2 <- consumer.hasReceiveMore
      } yield expect(msg1 == "B") and
        expect(hasMore1) and
        expect(msg2 == "We would like to see this") and
        expect(!hasMore2)

    create.use((program _).tupled)
  }

  test("subscribe to specific topic") { ctx =>
    withRandomPortSocket(ctx, Subscriber.Topic.utf8String("my-topic")) { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      val messages = List("0", "my-topic-1", "1", "my-topic2", "my-topic-3")

      for {
        _      <- Timer[IO].sleep(200.millis)
        _      <- messages.traverse(producer.send[String])
        result <- collectMessages(consumer, 3L)
      } yield expect(result == List("my-topic-1", "my-topic2", "my-topic-3"))
    }
  }

  test("subscribe to specific topic (bytes)") { ctx =>
    withRandomPortSocket(ctx, Subscriber.Topic.Bytes(Array(3, 1))) { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      val messages = List[Array[Byte]](Array(1), Array(2, 1, 3), Array(3, 1, 2), Array(3, 2, 1))

      for {
        _      <- Timer[IO].sleep(200.millis)
        _      <- messages.traverse(producer.send[Array[Byte]])
        result <- consumer.receive[Array[Byte]]
      } yield expect(result.sameElements(Array[Byte](3, 1, 2)))
    }
  }

  test("subscribe to all topics") { ctx =>
    withRandomPortSocket(ctx, Subscriber.Topic.All) { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      val messages = List("0", "my-topic-1", "1", "my-topic2", "my-topic-3")

      for {
        _      <- Timer[IO].sleep(200.millis)
        _      <- messages.traverse(producer.send[String])
        result <- collectMessages(consumer, messages.length.toLong)
      } yield expect(result == messages)
    }
  }

  def withRandomPortSocket[A](ctx: Context[IO], topic: Subscriber.Topic)(fa: SocketResource.Pair[IO] => IO[A]): IO[A] = {
    val uri = tcp_i"://localhost"

    (for {
      publisher  <- Resource.suspend(ctx.createPublisher.map(_.bindToRandomPort(uri)))
      subscriber <- Resource.suspend(ctx.createSubscriber(topic).map(_.connect(publisher.uri)))
    } yield SocketResource.Pair(publisher, subscriber)).use(fa)
  }

}
