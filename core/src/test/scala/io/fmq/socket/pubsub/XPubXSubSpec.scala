package io.fmq
package socket
package pubsub

import cats.effect.{IO, Resource, Timer}
import cats.instances.list._
import cats.syntax.traverse._
import io.fmq.address.{Address, Host, Port, Uri}
import io.fmq.frame.Frame
import org.scalatest.Assertion

import scala.concurrent.duration._

/**
  * Tests are using Timer[IO].sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
class XPubXSubSpec extends IOSpec with SocketBehavior {

  "XPubXSub" should {

    "topic pub sub" in withSockets { pair =>
      val XPubXSubSpec.Pair(pub, sub) = pair

      for {
        _      <- Timer[IO].sleep(200.millis)
        _      <- sub.sendSubscribe(Subscriber.Topic.utf8String("A"))
        _      <- Timer[IO].sleep(200.millis)
        subMsg <- pub.receive[Array[Byte]]
        _      <- pub.sendMultipart(Frame.Multipart("A", "Hello"))
        msg    <- sub.receiveFrame[String]
      } yield {
        subMsg shouldBe Array[Byte](XSubscriber.Subscribe, 'A')
        msg shouldBe Frame.Multipart("A", "Hello")
      }
    }

    "census" in withSockets { pair =>
      val XPubXSubSpec.Pair(pub, sub) = pair

      for {
        _    <- Timer[IO].sleep(200.millis)
        _    <- sub.send("Message from subscriber")
        msg1 <- pub.receive[String]
        _    <- sub.send(Array.emptyByteArray)
        msg2 <- pub.receive[Array[Byte]]
      } yield {
        msg1 shouldBe "Message from subscriber"
        msg2 shouldBe empty
      }
    }

    "simple pub sub" in withSockets { pair =>
      val XPubXSubSpec.Pair(pub, sub) = pair

      for {
        _   <- Timer[IO].sleep(200.millis)
        _   <- sub.sendSubscribe(Subscriber.Topic.All)
        _   <- Timer[IO].sleep(200.millis)
        _   <- pub.send("Hello")
        msg <- sub.receive[String]
      } yield msg shouldBe "Hello"
    }

    "not subscribed" in withSockets { pair =>
      val XPubXSubSpec.Pair(pub, sub) = pair

      for {
        _      <- Timer[IO].sleep(200.millis)
        _      <- pub.send("Hello")
        result <- sub.receiveNoWait[String]
      } yield result shouldBe empty
    }

    "multiple subscriptions" in withSockets { pair =>
      val XPubXSubSpec.Pair(pub, sub) = pair

      val topics   = List("A", "B", "C", "D", "E")
      val messages = topics.map(_ + "1")

      for {
        _        <- Timer[IO].sleep(200.millis)
        _        <- topics.traverse(topic => sub.sendSubscribe(Subscriber.Topic.utf8String(topic)))
        _        <- Timer[IO].sleep(200.millis)
        _        <- messages.traverse(pub.send[String])
        received <- collectMessages(sub, 5)
        _        <- topics.traverse(topic => sub.sendUnsubscribe(Subscriber.Topic.utf8String(topic)))
        _        <- Timer[IO].sleep(200.millis)
        _        <- messages.traverse(pub.send[String])
        result   <- sub.receiveNoWait[String]
      } yield {
        received shouldBe messages
        result shouldBe empty
      }
    }

    "multiple subscribers" in withContext() { ctx: Context[IO] =>
      val uri = Uri.Complete.TCP(Address.Full(Host.Fixed("localhost"), Port(53123)))

      val topics1 = List("A", "AB", "B", "C")
      val topics2 = List("A", "AB", "C")

      def program(input: (XPublisher.Socket[IO], XSubscriber.Socket[IO], XSubscriber.Socket[IO])): IO[Assertion] = {
        val (pub, sub1, sub2) = input

        for {
          _    <- Timer[IO].sleep(200.millis)
          _    <- topics1.traverse(topic => sub1.sendSubscribe(Subscriber.Topic.utf8String(topic)))
          _    <- topics2.traverse(topic => sub2.sendSubscribe(Subscriber.Topic.utf8String(topic)))
          _    <- Timer[IO].sleep(200.millis)
          _    <- pub.send("AB-1")
          msg1 <- sub1.receive[String]
          msg2 <- sub2.receive[String]
        } yield {
          msg1 shouldBe "AB-1"
          msg2 shouldBe "AB-1"
        }
      }

      (for {
        pub       <- Resource.liftF(ctx.createXPublisher)
        sub1      <- Resource.liftF(ctx.createXSubscriber)
        sub2      <- Resource.liftF(ctx.createXSubscriber)
        producer  <- pub.bind(uri)
        consumer1 <- sub1.connect(uri)
        consumer2 <- sub2.connect(uri)
      } yield (producer, consumer1, consumer2)).use(program)
    }

  }

  private def withSockets[A](fa: XPubXSubSpec.Pair[IO] => IO[A]): A =
    withContext() { ctx: Context[IO] =>
      val uri = Uri.Incomplete.TCP(Address.HostOnly(Host.Fixed("localhost")))

      (for {
        pub      <- Resource.liftF(ctx.createXPublisher)
        sub      <- Resource.liftF(ctx.createXSubscriber)
        producer <- pub.bindToRandomPort(uri)
        consumer <- sub.connect(producer.uri)
      } yield XPubXSubSpec.Pair(producer, consumer)).use(fa)
    }

}

object XPubXSubSpec {

  final case class Pair[F[_]](
      publisher: XPublisher.Socket[F],
      subscriber: XSubscriber.Socket[F]
  )

}
