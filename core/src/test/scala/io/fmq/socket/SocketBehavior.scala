package io.fmq
package socket

import cats.effect.syntax.effect._
import cats.effect.{IO, Resource, Sync, Timer}
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.traverse._
import fs2.Stream
import io.fmq.address.{Address, Port, Protocol, Uri}
import io.fmq.options._
import io.fmq.socket.SocketBehavior.{Consumer, Producer, SocketResource}
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SendOptions}
import org.scalatest.Assertion
import org.scalatest.Inside._

import scala.concurrent.duration._

/**
  * Tests are using Timer[IO].sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
trait SocketBehavior {
  self: IOSpec =>

  protected def socketSpec[PR <: Protocol, ADDR <: Address, P <: Producer[IO], C <: Consumer[IO]](
      socketResource: SocketResource[IO, PR, ADDR, P, C]
  ): Unit = {

    "send multipart data" in withRandomPortPair { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      val program =
        for {
          _        <- producer.sendStringMore("A")
          _        <- producer.sendString("We would like to see this")
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

      val resource =
        for {
          producer <- socketResource.createProducer(ctx)
          consumer <- socketResource.createConsumer(ctx)
          pair     <- socketResource.bind(producer, consumer, port)
        } yield pair

      resource.use {
        case SocketResource.Pair(producer, consumer) =>
          val expectedUri = socketResource.expectedRandomUri(port)

          producer.uri shouldBe expectedUri
          consumer.uri shouldBe expectedUri

          val program =
            for {
              _      <- messages.traverse(producer.sendString)
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
          _      <- messages.traverse(producer.sendString)
          result <- collectMessages(consumer, messages.length.toLong)
        } yield result shouldBe messages

      Timer[IO].sleep(200.millis) >> program.toIO
    }

    "operate sendTimeout" in withContext() { context: Context[IO] =>
      def program(socket: SendOptions.All[IO]): IO[Assertion] = {
        def changeTimeout(timeout: SendTimeout): IO[SendTimeout] =
          socket.setSendTimeout(timeout) >> socket.sendTimeout

        for {
          timeout1 <- changeTimeout(SendTimeout.Immediately)
          timeout2 <- changeTimeout(SendTimeout.Infinity)
          timeout3 <- changeTimeout(SendTimeout.Fixed(5.seconds))
        } yield {
          timeout1 shouldBe SendTimeout.Immediately
          timeout2 shouldBe SendTimeout.Infinity
          timeout3 shouldBe SendTimeout.Fixed(5.seconds)
        }
      }

      socketResource.createProducer(context).use(program)
    }

    "operate receiveTimeout" in withContext() { context: Context[IO] =>
      def program(socket: ReceiveOptions.All[IO]): IO[Assertion] = {
        def changeTimeout(timeout: ReceiveTimeout): IO[ReceiveTimeout] =
          socket.setReceiveTimeout(timeout) >> socket.receiveTimeout

        for {
          timeout1 <- changeTimeout(ReceiveTimeout.Immediately)
          timeout2 <- changeTimeout(ReceiveTimeout.Infinity)
          timeout3 <- changeTimeout(ReceiveTimeout.Fixed(5.seconds))
        } yield {
          timeout1 shouldBe ReceiveTimeout.Immediately
          timeout2 shouldBe ReceiveTimeout.Infinity
          timeout3 shouldBe ReceiveTimeout.Fixed(5.seconds)
        }
      }

      socketResource.createConsumer(context).use(program)
    }

    "operate linger" in withContext() { context: Context[IO] =>
      def program(socket: CommonOptions.All[IO]): IO[Assertion] = {
        def changeLinger(linger: Linger): IO[Linger] =
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

      socketResource.createProducer(context).use(program) >> socketResource.createConsumer(context).use(program)
    }

    "operate identity" in withContext() { context: Context[IO] =>
      val identity = Identity(Array(1, 2, 3))

      def program(socket: CommonOptions.All[IO]): IO[Assertion] =
        for {
          identity1 <- socket.setIdentity(identity) >> socket.identity
        } yield {
          inside(identity1) {
            case Identity(value) => value should not be empty
          }
        }

      socketResource.createProducer(context).use(program) >> socketResource.createConsumer(context).use(program)
    }

    def withRandomPortPair[A](fa: SocketResource.Pair[IO, PR, ADDR] => IO[A]): A =
      withContext() { ctx: Context[IO] =>
        (for {
          producer <- socketResource.createProducer(ctx)
          consumer <- socketResource.createConsumer(ctx)
          pair     <- socketResource.bindToRandom(producer, consumer)
        } yield pair).use(fa)
      }

  }

  protected def collectMessages[F[_]: Sync, P <: Protocol, A <: Address](
      consumer: ConsumerSocket[F, P, A],
      limit: Long
  ): F[List[String]] =
    Stream.repeatEval(consumer.recvString).take(limit).compile.toList

}

object SocketBehavior {

  type Producer[F[_]] = SendOptions.All[F] with CommonOptions.All[F]
  type Consumer[F[_]] = ReceiveOptions.All[F] with CommonOptions.All[F]

  trait SocketResource[F[_], PR <: Protocol, A <: Address, P <: Producer[F], C <: Consumer[F]] {

    type Pair = SocketResource.Pair[F, PR, A]

    def createProducer(context: Context[F]): Resource[F, P]
    def createConsumer(context: Context[F]): Resource[F, C]
    def bind(producer: P, consumer: C, port: Port): Resource[F, Pair]
    def bindToRandom(producer: P, consumer: C): Resource[F, Pair]

    def expectedRandomUri(port: Port): Uri[PR, A]

  }

  object SocketResource {

    final case class Pair[F[_], P <: Protocol, A <: Address](
        producer: ProducerSocket[F, P, A],
        consumer: ConsumerSocket[F, P, A]
    )

  }

}
