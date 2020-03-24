package io.fmq.socket.pubsub

import cats.effect.{IO, Timer}
import cats.instances.list._
import cats.syntax.traverse._
import io.fmq.address.{Address, Host, Protocol, Uri}
import io.fmq.socket.{SocketBehavior, Subscriber, XPublisherSocket, XSubscriberSocket}
import io.fmq.{Context, IOSpec}
import org.scalatest.Assertion

import scala.concurrent.duration._

class XPubXSubSpec extends IOSpec with SocketBehavior {

  "XPubXSub" should {

    "topic pub sub" in withSockets { pair =>
      val XPubXSubSpec.Pair(pub, sub) = pair

      for {
        _        <- Timer[IO].sleep(200.millis)
        _        <- sub.sendSubscribe(Subscriber.Topic.utf8String("A"))
        _        <- Timer[IO].sleep(200.millis)
        subMsg   <- pub.recv
        _        <- pub.sendStringMore("A")
        _        <- pub.sendString("Hello")
        msg1     <- sub.recvString
        hasMore1 <- sub.hasReceiveMore
        msg2     <- sub.recvString
        hasMore2 <- sub.hasReceiveMore
      } yield {
        subMsg shouldBe Array[Byte](XSubscriberSocket.Subscribe, 'A')
        msg1 shouldBe "A"
        hasMore1 shouldBe true
        msg2 shouldBe "Hello"
        hasMore2 shouldBe false
      }
    }

    "census" in withSockets { pair =>
      val XPubXSubSpec.Pair(pub, sub) = pair

      for {
        _    <- Timer[IO].sleep(200.millis)
        _    <- sub.sendString("Message from subscriber")
        msg1 <- pub.recvString
        _    <- sub.send(Array.emptyByteArray)
        msg2 <- pub.recv
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
        _   <- pub.sendString("Hello")
        msg <- sub.recvString
      } yield msg shouldBe "Hello"
    }

    "not subscribed" in withSockets { pair =>
      val XPubXSubSpec.Pair(pub, sub) = pair

      for {
        _      <- Timer[IO].sleep(200.millis)
        _      <- pub.sendString("Hello")
        result <- sub.recvNoWait
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
        _        <- messages.traverse(pub.sendString)
        received <- collectMessages(sub, 5)
        _        <- topics.traverse(topic => sub.sendUnsubscribe(Subscriber.Topic.utf8String(topic)))
        _        <- Timer[IO].sleep(200.millis)
        _        <- messages.traverse(pub.sendString)
        result   <- sub.recvNoWait
      } yield {
        received shouldBe messages
        result shouldBe empty
      }
    }

    "multiple subscribers" in withContext() { ctx: Context[IO] =>
      val uri = Uri.tcp(Address.HostOnly(Host.Fixed("localhost")))

      val topics1 = List("A", "AB", "B", "C")
      val topics2 = List("A", "AB", "C")

      def program(input: (XPublisherSocket.TCP[IO], XSubscriberSocket.TCP[IO], XSubscriberSocket.TCP[IO])): IO[Assertion] = {
        val (pub, sub1, sub2) = input

        for {
          _    <- Timer[IO].sleep(200.millis)
          _    <- topics1.traverse(topic => sub1.sendSubscribe(Subscriber.Topic.utf8String(topic)))
          _    <- topics2.traverse(topic => sub2.sendSubscribe(Subscriber.Topic.utf8String(topic)))
          _    <- Timer[IO].sleep(200.millis)
          _    <- pub.sendString("AB-1")
          msg1 <- sub1.recvString
          msg2 <- sub2.recvString
        } yield {
          msg1 shouldBe "AB-1"
          msg2 shouldBe "AB-1"
        }
      }

      (for {
        pub       <- ctx.createXPublisher
        sub1      <- ctx.createXSubscriber
        sub2      <- ctx.createXSubscriber
        producer  <- pub.bindToRandomPort(uri)
        consumer1 <- sub1.connect(producer.uri)
        consumer2 <- sub2.connect(producer.uri)
      } yield (producer, consumer1, consumer2)).use(program)
    }

  }

  private def withSockets[A](fa: XPubXSubSpec.Pair[IO, Protocol.TCP, Address.Full] => IO[A]): A =
    withContext() { ctx: Context[IO] =>
      val uri = Uri.tcp(Address.HostOnly(Host.Fixed("localhost")))

      (for {
        pub      <- ctx.createXPublisher
        sub      <- ctx.createXSubscriber
        producer <- pub.bindToRandomPort(uri)
        consumer <- sub.connect(producer.uri)
      } yield XPubXSubSpec.Pair(producer, consumer)).use(fa)
    }

}

object XPubXSubSpec {

  final case class Pair[F[_], P <: Protocol, A <: Address](
      publisher: XPublisherSocket[F, P, A],
      subscriber: XSubscriberSocket[F, P, A]
  )

}
