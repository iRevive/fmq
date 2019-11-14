package io.fmq.socket

import cats.effect.{IO, Resource, Timer}
import cats.instances.list._
import cats.syntax.traverse._
import cats.syntax.flatMap._
import fs2.Stream
import io.fmq.socket.SocketBehavior.MkSocket
import io.fmq.domain.{Address, Port, Protocol, SubscribeTopic}
import io.fmq.{Context, IOSpec}

import scala.concurrent.duration._

/**
  * Tests are using Timer[IO].sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
class PublisherSpec extends IOSpec with SocketBehavior {

  "Publisher" should {

    "send multipart data" in withContext() { ctx =>
      val address = Address.RandomPort(Protocol.TCP, "localhost")
      val topic   = SubscribeTopic.utf8String("B")

      def sendA(publisher: Publisher.Connected[IO]): IO[Unit] =
        publisher.sendUtf8StringMore("A") >> publisher.sendUtf8String("We don't want to see this")

      def sendB(publisher: Publisher.Connected[IO]): IO[Unit] =
        publisher.sendUtf8StringMore("B") >> publisher.sendUtf8String("We would like to see this")

      for {
        pub        <- ctx.createPublisher
        publisher  <- pub.bind(address)
        sub        <- ctx.createSubscriber(topic)
        subscriber <- sub.connect(Address.Const(Protocol.TCP, "localhost", publisher.port))
        _          <- Resource.liftF(Timer[IO].sleep(200.millis))
        _          <- Resource.liftF(sendA(publisher) >> sendB(publisher))
        msg1       <- Resource.liftF(subscriber.recvString)
        hasMore1   <- Resource.liftF(subscriber.hasReceiveMore)
        msg2       <- Resource.liftF(subscriber.recvString)
        hasMore2   <- Resource.liftF(subscriber.hasReceiveMore)
      } yield {
        msg1 shouldBe "B"
        hasMore1 shouldBe true
        msg2 shouldBe "We would like to see this"
        hasMore2 shouldBe false
      }
    }

    "bind to specific port" in withContext() { ctx =>
      val address  = Address.Const(Protocol.TCP, "localhost", Port(31243))
      val topic    = SubscribeTopic.All
      val messages = List("0", "my-topic-1", "1", "my-topic2", "my-topic-3")

      for {
        pub        <- ctx.createPublisher
        sub        <- ctx.createSubscriber(topic)
        publisher  <- pub.bind(address)
        subscriber <- sub.connect(address)
        _          <- Resource.liftF(Timer[IO].sleep(200.millis))
        _          <- Resource.liftF(messages.traverse(publisher.sendUtf8String))
        result     <- Resource.liftF(collectMessages(subscriber, messages.length.toLong))
      } yield result shouldBe messages
    }

    "bind to random port" in withContext() { ctx =>
      val address  = Address.RandomPort(Protocol.TCP, "*")
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

    behave like supportedOperations(mkPublisher)

  }

  private def collectMessages(subscriber: Subscriber.Connected[IO], limit: Long): IO[List[String]] =
    Stream.repeatEval(subscriber.recvString).take(limit).compile.toList

  private lazy val mkPublisher: MkSocket[Publisher[IO]] = (context: Context[IO]) => context.createPublisher

}
