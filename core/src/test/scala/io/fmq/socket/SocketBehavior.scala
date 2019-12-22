package io.fmq
package socket

import cats.effect.syntax.effect._
import cats.effect.{Effect, IO, Resource, Sync, Timer}
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import fs2.Stream
import io.fmq.domain._
import io.fmq.socket.SocketBehavior.SocketResource
import io.fmq.socket.api.CommonOptions
import org.scalatest.Inside._

import scala.concurrent.duration._

/**
  * Tests are using Timer[IO].sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
trait SocketBehavior {
  self: IOSpec =>

  protected def socketSpec[H[_]: Sync: Effect](socketResource: SocketResource[IO, H]): Unit = {

    "send multipart data" in withRandomPortPair { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      val program =
        for {
          _        <- producer.sendUtf8StringMore("A")
          _        <- producer.sendUtf8String("We would like to see this")
          msg1     <- consumer.recvString
          hasMore1 <- consumer.hasReceiveMore
          msg2     <- consumer.recvString
          hasMore2 <- consumer.hasReceiveMore
        } yield {
          msg1 shouldBe "A"
          hasMore1 shouldBe true
          msg2 shouldBe "We would like to see this"
          hasMore2 shouldBe false
        }

      Timer[IO].sleep(200.millis) >> program.toIO
    }

    "bind to specific port" in withContext() { ctx: Context[IO] =>
      val port     = Port(31243)
      val messages = List("0", "my-topic-1", "1", "my-topic2", "my-topic-3")

      socketResource.bind(ctx, port).use {
        case SocketResource.Pair(producer, consumer) =>
          producer.port shouldBe port
          consumer.port shouldBe port

          val program =
            for {
              _      <- messages.traverse(producer.sendUtf8String)
              result <- collectMessages(consumer, messages.length.toLong)
            } yield result shouldBe messages

          Timer[IO].sleep(200.millis) >> program.toIO
      }
    }

    "bind to random port" in withRandomPortPair { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      val messages = List("0", "my-topic-1", "1", "my-topic2", "my-topic-3")

      val program =
        for {
          _      <- messages.traverse(producer.sendUtf8String)
          result <- collectMessages(consumer, messages.length.toLong)
        } yield result shouldBe messages

      Timer[IO].sleep(200.millis) >> program.toIO
    }

    "operate sendTimeout" in withRandomPortPair { pair =>
      val SocketResource.Pair(producer, _) = pair

      def changeTimeout(timeout: SendTimeout): H[SendTimeout] =
        producer.setSendTimeout(timeout) >> producer.sendTimeout

      val program = for {
        timeout1 <- changeTimeout(SendTimeout.Immediately)
        timeout2 <- changeTimeout(SendTimeout.Infinity)
        timeout3 <- changeTimeout(SendTimeout.Fixed(5.seconds))
      } yield {
        timeout1 shouldBe SendTimeout.Immediately
        timeout2 shouldBe SendTimeout.Infinity
        timeout3 shouldBe SendTimeout.Fixed(5.seconds)
      }

      Timer[IO].sleep(200.millis) >> program.toIO
    }

    "operate receiveTimeout" in withRandomPortPair { pair =>
      val SocketResource.Pair(_, consumer) = pair

      def changeTimeout(timeout: ReceiveTimeout): H[ReceiveTimeout] =
        consumer.setReceiveTimeout(timeout) >> consumer.receiveTimeout

      val program = for {
        timeout1 <- changeTimeout(ReceiveTimeout.Immediately)
        timeout2 <- changeTimeout(ReceiveTimeout.Infinity)
        timeout3 <- changeTimeout(ReceiveTimeout.Fixed(5.seconds))
      } yield {
        timeout1 shouldBe ReceiveTimeout.Immediately
        timeout2 shouldBe ReceiveTimeout.Infinity
        timeout3 shouldBe ReceiveTimeout.Fixed(5.seconds)
      }

      Timer[IO].sleep(200.millis) >> program.toIO
    }

    "operate linger" in withRandomPortPair { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      def program(socket: CommonOptions[H]) = {
        def changeLinger(linger: Linger): H[Linger] =
          socket.setLinger(linger) >> socket.linger

        for {
          linger1 <- changeLinger(Linger.Immediately)
          linger2 <- changeLinger(Linger.Infinity)
          linger3 <- changeLinger(Linger.Fixed(5.seconds))
        } yield {
          linger1 shouldBe Linger.Immediately
          linger2 shouldBe Linger.Infinity
          linger3 shouldBe Linger.Fixed(5.seconds)
        }
      }

      Timer[IO].sleep(200.millis) >> program(producer).toIO >> program(consumer).toIO
    }

    "operate identity" in withRandomPortPair { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      val identity = Identity(Array(1, 2, 3))

      def program(socket: CommonOptions[H]) =
        for {
          identity1 <- socket.setIdentity(identity) >> socket.identity
        } yield {
          inside(identity1) {
            case Identity(value) => value should not be empty
          }
        }

      Timer[IO].sleep(200.millis) >> program(producer).toIO >> program(consumer).toIO
    }

    def withRandomPortPair[A](fa: SocketResource.Pair[H] => IO[A]): A =
      withContext() { ctx: Context[IO] =>
        socketResource.bindToRandomPort(ctx).use(fa)
      }

  }

  protected def collectMessages[F[_]: Sync](consumer: ConsumerSocket[F], limit: Long): F[List[String]] =
    Stream.repeatEval(consumer.recvString).take(limit).compile.toList

}

object SocketBehavior {

  trait SocketResource[F[_], H[_]] {
    def bind(context: Context[F], port: Port): Resource[F, SocketResource.Pair[H]]
    def bindToRandomPort(context: Context[F]): Resource[F, SocketResource.Pair[H]]
  }

  object SocketResource {
    final case class Pair[F[_]](producer: ProducerSocket[F], consumer: ConsumerSocket[F])
  }

}
