package io.fmq.socket

import cats.effect.{IO, Resource, Timer}
import cats.instances.list._
import cats.syntax.traverse._
import fs2.Stream
import io.fmq.domain.{Address, Protocol, SubscribeTopic}
import io.fmq.socket.SocketBehavior.MkSocket
import io.fmq.{Context, IOSpec}

import scala.concurrent.duration._

/**
  * Tests are using Timer[IO].sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
class SubscriberSpec extends IOSpec with SocketBehavior {

  "Subscriber" should {

    "subscribe to specific topic" in withContext() { ctx =>
      val address  = Address.RandomPort(Protocol.TCP, "localhost")
      val topic    = SubscribeTopic.utf8String("my-topic")
      val messages = List("0", "my-topic-1", "1", "my-topic2", "my-topic-3")

      for {
        pub        <- ctx.createPublisher
        sub        <- ctx.createSubscriber(topic)
        publisher  <- pub.bind(address)
        subscriber <- sub.connect(Address.Const(Protocol.TCP, "localhost", publisher.port))
        _          <- Resource.liftF(Timer[IO].sleep(200.millis))
        _          <- Resource.liftF(messages.traverse(publisher.sendUtf8String))
        result     <- Resource.liftF(collectMessages(subscriber, 3L))
      } yield result shouldBe List("my-topic-1", "my-topic2", "my-topic-3")
    }

    "subscribe to specific topic (bytes)" in withContext() { ctx =>
      val address  = Address.RandomPort(Protocol.TCP, "localhost")
      val topic    = SubscribeTopic.Bytes(Array(3, 1))
      val messages = List[Array[Byte]](Array(1), Array(2, 1, 3), Array(3, 1, 2), Array(3, 2, 1))

      for {
        pub        <- ctx.createPublisher
        sub        <- ctx.createSubscriber(topic)
        publisher  <- pub.bind(address)
        subscriber <- sub.connect(Address.Const(Protocol.TCP, "localhost", publisher.port))
        _          <- Resource.liftF(Timer[IO].sleep(200.millis))
        _          <- Resource.liftF(messages.traverse(publisher.send))
        result     <- Resource.liftF(subscriber.recv)
      } yield result shouldBe Array[Byte](3, 1, 2)
    }

    "subscribe to all topics" in withContext() { ctx =>
      val address  = Address.RandomPort(Protocol.TCP, "localhost")
      val topic    = SubscribeTopic.All
      val messages = List("0", "my-topic-1", "1", "my-topic2", "my-topic-3")

      for {
        pub        <- ctx.createPublisher
        sub        <- ctx.createSubscriber(topic)
        publisher  <- pub.bind(address)
        subscriber <- sub.connect(Address.Const(Protocol.TCP, "localhost", publisher.port))
        _          <- Resource.liftF(Timer[IO].sleep(200.millis))
        _          <- Resource.liftF(messages.traverse(publisher.sendUtf8String))
        result     <- Resource.liftF(collectMessages(subscriber, messages.length.toLong))
      } yield result shouldBe messages
    }

    behave like supportedOperations(mkSubscriber)

  }

  private def collectMessages(subscriber: Subscriber.Connected[IO], limit: Long): IO[List[String]] =
    Stream.repeatEval(subscriber.recvString).take(limit).compile.toList

  private lazy val mkSubscriber: MkSocket[Subscriber[IO]] =
    (context: Context[IO]) => context.createSubscriber(SubscribeTopic.All)

}
