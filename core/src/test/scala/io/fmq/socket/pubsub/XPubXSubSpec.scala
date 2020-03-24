package io.fmq
package socket
package pubsub

import cats.effect.{IO, Timer}
import cats.instances.list._
import cats.syntax.traverse._
import io.fmq.address.{Address, Host, Port, Protocol, Uri}
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
        subMsg   <- pub.receive[Array[Byte]]
        _        <- pub.sendMore("A")
        _        <- pub.send("Hello")
        msg1     <- sub.receive[String]
        hasMore1 <- sub.hasReceiveMore
        msg2     <- sub.receive[String]
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
      val uri = Uri.tcp(Address.Full(Host.Fixed("localhost"), Port(53123)))

      val topics1 = List("A", "AB", "B", "C")
      val topics2 = List("A", "AB", "C")

      def program(input: (XPublisherSocket.TCP[IO], XSubscriberSocket.TCP[IO], XSubscriberSocket.TCP[IO])): IO[Assertion] = {
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
        pub       <- ctx.createXPublisher
        sub1      <- ctx.createXSubscriber
        sub2      <- ctx.createXSubscriber
        producer  <- pub.bind(uri)
        consumer1 <- sub1.connect(uri)
        consumer2 <- sub2.connect(uri)
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
