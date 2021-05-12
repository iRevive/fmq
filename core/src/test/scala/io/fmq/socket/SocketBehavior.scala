package io.fmq
package socket

import cats.effect.{IO, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.traverse._
import fs2.Stream
import io.fmq.address.Uri
import io.fmq.frame.Frame
import io.fmq.options._
import io.fmq.socket.SocketBehavior.{Consumer, Producer, SocketResource}
import io.fmq.socket.api.{CommonOptions, ReceiveOptions, SendOptions}
import weaver.Expectations

import scala.concurrent.duration._

/**
  * Tests are using IO.sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
trait SocketBehavior {
  self: ContextSuite =>

  protected def socketSpec[P <: Producer[IO], C <: Consumer[IO]](name: String, socketResource: SocketResource[IO, P, C]): Unit = {

    test(s"$name. send multipart data") { ctx =>
      withRandomPortPair(ctx) { case SocketResource.Pair(producer, consumer) =>
        val program =
          for {
            _   <- producer.sendMultipart(Frame.Multipart("A", "We would like to see this"))
            msg <- consumer.receiveFrame[String]
          } yield expect(msg == Frame.Multipart("A", "We would like to see this"))

        IO.sleep(200.millis) >> program
      }
    }

    test(s"$name. bind to specific port") { ctx =>
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

        val program =
          for {
            _      <- messages.traverse(producer.send[String])
            result <- collectMessages(consumer, messages.length.toLong)
          } yield expect(producer.uri == expectedUri) and
            expect(consumer.uri == expectedUri) and
            expect(result == messages)

        IO.sleep(200.millis) >> program
      }
    }

    test(s"$name. bind to random port") { ctx =>
      withRandomPortPair(ctx) { case SocketResource.Pair(producer, consumer) =>
        val messages = List("0", "my-topic-1", "1", "my-topic2", "my-topic-3")

        val program =
          for {
            _      <- messages.traverse(producer.send[String])
            result <- collectMessages(consumer, messages.length.toLong)
          } yield expect(result == messages)

        IO.sleep(200.millis) >> program
      }
    }

    test(s"$name. operate sendTimeout") { ctx =>
      def program(socket: SendOptions.All[IO]): IO[Expectations] = {
        def changeTimeout(timeout: SendTimeout): IO[SendTimeout] =
          socket.setSendTimeout(timeout) >> socket.sendTimeout

        for {
          timeout1 <- changeTimeout(SendTimeout.Immediately)
          timeout2 <- changeTimeout(SendTimeout.Infinity)
          timeout3 <- changeTimeout(SendTimeout.Fixed(5.seconds))
        } yield expect(timeout1 == SendTimeout.Immediately) and
          expect(timeout2 == SendTimeout.Infinity) and
          expect(timeout3 == SendTimeout.Fixed(5.seconds))
      }

      socketResource.createProducer(ctx) >>= program
    }

    test(s"$name. operate receiveTimeout") { ctx =>
      def program(socket: ReceiveOptions.All[IO]): IO[Expectations] = {
        def changeTimeout(timeout: ReceiveTimeout): IO[ReceiveTimeout] =
          socket.setReceiveTimeout(timeout) >> socket.receiveTimeout

        for {
          timeout1 <- changeTimeout(ReceiveTimeout.Immediately)
          timeout2 <- changeTimeout(ReceiveTimeout.Infinity)
          timeout3 <- changeTimeout(ReceiveTimeout.Fixed(5.seconds))
        } yield expect(timeout1 == ReceiveTimeout.Immediately) and
          expect(timeout2 == ReceiveTimeout.Infinity) and
          expect(timeout3 == ReceiveTimeout.Fixed(5.seconds))
      }

      socketResource.createConsumer(ctx) >>= program
    }

    test(s"$name. operate linger") { ctx =>
      def program(socket: CommonOptions.All[IO]): IO[Expectations] = {
        def changeLinger(linger: Linger): IO[Linger] =
          socket.setLinger(linger) >> socket.linger

        for {
          linger1 <- changeLinger(Linger.Immediately)
          linger2 <- changeLinger(Linger.Infinity)
          linger3 <- changeLinger(Linger.Fixed(5.seconds))
        } yield expect(linger1 == Linger.Immediately) and
          expect(linger2 == Linger.Infinity) and
          expect(linger3 == Linger.Fixed(5.seconds))
      }

      (socketResource.createProducer(ctx) >>= program) >> (socketResource.createConsumer(ctx) >>= program)
    }

    test(s"$name. operate receive high water mark") { ctx =>
      def program(socket: ReceiveOptions.All[IO]): IO[Expectations] = {
        def changeWaterMark(hwm: HighWaterMark): IO[HighWaterMark] =
          socket.setReceiveHighWaterMark(hwm) >> socket.receiveHighWaterMark

        for {
          hwm1 <- changeWaterMark(HighWaterMark.NoLimit)
          hwm2 <- changeWaterMark(HighWaterMark.Limit(10))
        } yield expect(hwm1 == HighWaterMark.NoLimit) and expect(hwm2 == HighWaterMark.Limit(10))
      }

      socketResource.createConsumer(ctx) >>= program
    }

    test(s"$name. operate send high water mark") { ctx =>
      def program(socket: SendOptions.All[IO]): IO[Expectations] = {
        def changeWaterMark(hwm: HighWaterMark): IO[HighWaterMark] =
          socket.setSendHighWaterMark(hwm) >> socket.sendHighWaterMark

        for {
          hwm1 <- changeWaterMark(HighWaterMark.NoLimit)
          hwm2 <- changeWaterMark(HighWaterMark.Limit(10))
        } yield expect(hwm1 == HighWaterMark.NoLimit) and expect(hwm2 == HighWaterMark.Limit(10))
      }

      socketResource.createProducer(ctx) >>= program
    }

    def withRandomPortPair[A](ctx: Context[IO])(fa: SocketResource.Pair[IO] => IO[A]): IO[A] =
      (for {
        producer <- Resource.eval(socketResource.createProducer(ctx))
        consumer <- Resource.eval(socketResource.createConsumer(ctx))
        pair     <- socketResource.bindToRandom(producer, consumer)
      } yield pair).use(fa)

  }

  protected def collectMessages[F[_]: Sync](consumer: ConsumerSocket[F], limit: Long): F[List[String]] =
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
