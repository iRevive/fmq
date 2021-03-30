package io.fmq
package socket

import cats.effect.syntax.effect._
import cats.effect.{IO, Resource, Sync, Timer}
import cats.syntax.flatMap._
import cats.syntax.traverse._
import fs2.Stream
import io.fmq.address.Uri
import io.fmq.frame.Frame
import io.fmq.options._
import io.fmq.socket.SocketBehavior.{Consumer, Producer, SocketResource}
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SendOptions}
import org.scalatest.Assertion

import scala.concurrent.duration._

/**
  * Tests are using Timer[IO].sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
trait SocketBehavior {
  self: IOSpec =>

  protected def socketSpec[P <: Producer[IO], C <: Consumer[IO]](socketResource: SocketResource[IO, P, C]): Unit = {

    "send multipart data" in withRandomPortPair { pair =>
      val SocketResource.Pair(producer, consumer) = pair

      val program =
        for {
          _   <- producer.sendMultipart(Frame.Multipart("A", "We would like to see this"))
          msg <- consumer.receiveFrame[String]
        } yield msg shouldBe Frame.Multipart("A", "We would like to see this")

      Timer[IO].sleep(200.millis) >> program.toIO
    }

    "bind to specific port" in withContext() { ctx: Context[IO] =>
      val port     = 31243
      val messages = List("0", "my-topic-1", "1", "my-topic2", "my-topic-3")

      val resource =
        for {
          producer <- Resource.eval(socketResource.createProducer(ctx))
          consumer <- Resource.eval(socketResource.createConsumer(ctx))
          pair     <- socketResource.bind(producer, consumer, port)
        } yield pair

      resource.use { case SocketResource.Pair(producer, consumer) =>
        val expectedUri = socketResource.expectedRandomUri(port)

        producer.uri shouldBe expectedUri
        consumer.uri shouldBe expectedUri

        val program =
          for {
            _      <- messages.traverse(producer.send[String])
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
          _      <- messages.traverse(producer.send[String])
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

      socketResource.createProducer(context) >>= program
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

      socketResource.createConsumer(context) >>= program
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

      (socketResource.createProducer(context) >>= program) >> (socketResource.createConsumer(context) >>= program)
    }

    "operate receive high water mark" in withContext() { context: Context[IO] =>
      def program(socket: ReceiveOptions.All[IO]): IO[Assertion] = {
        def changeWaterMark(hwm: HighWaterMark): IO[HighWaterMark] =
          socket.setReceiveHighWaterMark(hwm) >> socket.receiveHighWaterMark

        for {
          hwm1 <- changeWaterMark(HighWaterMark.NoLimit)
          hwm2 <- changeWaterMark(HighWaterMark.Limit(10))
        } yield {
          hwm1 shouldBe HighWaterMark.NoLimit
          hwm2 shouldBe HighWaterMark.Limit(10)
        }
      }

      socketResource.createConsumer(context) >>= program
    }

    "operate send high water mark" in withContext() { context: Context[IO] =>
      def program(socket: SendOptions.All[IO]): IO[Assertion] = {
        def changeWaterMark(hwm: HighWaterMark): IO[HighWaterMark] =
          socket.setSendHighWaterMark(hwm) >> socket.sendHighWaterMark

        for {
          hwm1 <- changeWaterMark(HighWaterMark.NoLimit)
          hwm2 <- changeWaterMark(HighWaterMark.Limit(10))
        } yield {
          hwm1 shouldBe HighWaterMark.NoLimit
          hwm2 shouldBe HighWaterMark.Limit(10)
        }
      }

      socketResource.createProducer(context) >>= program
    }

    def withRandomPortPair[A](fa: SocketResource.Pair[IO] => IO[A]): A =
      withContext() { ctx: Context[IO] =>
        (for {
          producer <- Resource.eval(socketResource.createProducer(ctx))
          consumer <- Resource.eval(socketResource.createConsumer(ctx))
          pair     <- socketResource.bindToRandom(producer, consumer)
        } yield pair).use(fa)
      }

  }

  protected def collectMessages[F[_]: Sync](
      consumer: ConsumerSocket[F],
      limit: Long
  ): F[List[String]] =
    Stream.repeatEval(consumer.receive[String]).take(limit).compile.toList

}

object SocketBehavior {

  type Producer[F[_]] = SendOptions.All[F] with CommonOptions.All[F]
  type Consumer[F[_]] = ReceiveOptions.All[F] with CommonOptions.All[F]

  trait SocketResource[F[_], P <: Producer[F], C <: Consumer[F]] {

    type Pair = SocketResource.Pair[F]

    def createProducer(context: Context[F]): F[P]
    def createConsumer(context: Context[F]): F[C]
    def bind(producer: P, consumer: C, port: Int): Resource[F, Pair]
    def bindToRandom(producer: P, consumer: C): Resource[F, Pair]

    def expectedRandomUri(port: Int): Uri.Complete

  }

  object SocketResource {

    final case class Pair[F[_]](
        producer: ProducerSocket[F],
        consumer: ConsumerSocket[F]
    )

  }

}
