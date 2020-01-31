package io.fmq

import java.util.concurrent.TimeUnit

import cats.effect.syntax.concurrent._
import cats.effect.{Blocker, Clock, Concurrent, ContextShift, Effect, ExitCode, IO, IOApp, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Stream
import fs2.concurrent.Queue
import io.fmq.address.{Address, Host, Port, Uri}

object LocalThrScala extends IOApp {

  override def run(argv: List[String]): IO[ExitCode] = {
    val messageSize  = argv(1).toInt
    val messageCount = argv(2).toLong
    val uri          = Uri.tcp(Address.Full(Host.Fixed("localhost"), Port(5555)))

    Blocker[IO]
      .flatMap(blocker => Context.create[IO](1, blocker).tupleRight(blocker))
      .use {
        case (ctx, blocker) =>
          val ioStream = ctx.createPull
            .flatMap(_.bind(uri))
            .use(socket => measureStream(socket, messageCount, messageSize))

          val ioCycle = ctx.createPull
            .flatMap(_.bind(uri))
            .use(socket => measureCycle(socket, messageCount, messageSize))

          val ioBackground = ctx.createPull
            .flatMap(_.bind(uri))
            .use(socket => measureBackground(socket, blocker, messageCount, messageSize))

          for {
            _ <- log[IO]("Measure fs2.Stream[IO] performance") >> log[IO]("")
            _ <- ioStream
            _ <- log[IO]("Measure IO cycle performance") >> log[IO]("")
            _ <- ioCycle
            _ <- log[IO]("Measure fs2.Stream[IO] background performance") >> log[IO]("")
            _ <- ioBackground
          } yield ExitCode.Success
      }

  }

  def measureStream[F[_]: Sync: Clock](pull: ConsumerSocket.TCP[F], messageCount: Long, messageSize: Int): F[Unit] = {
    val io = Stream
      .eval(pull.recv)
      .repeatN(messageCount)
      .compile
      .lastOrError

    for {
      _ <- pull.recv
      _ <- measureTime(io, messageCount, messageSize)
    } yield ()
  }

  def measureCycle[F[_]: Sync: Clock: Effect](pull: ConsumerSocket.TCP[F], messageCount: Long, messageSize: Int): IO[Unit] = {
    val io = IO.delay {
      for (_ <- 0 until messageCount.toInt - 1) {
        Effect[F].toIO(pull.recv).unsafeRunSync()
      }
    }

    for {
      _ <- Effect[F].toIO(pull.recv)
      _ <- measureTime(io, messageCount, messageSize)
    } yield ()
  }

  def measureBackground[F[_]: Concurrent: ContextShift: Clock](
      pull: ConsumerSocket.TCP[F],
      blocker: Blocker,
      messageCount: Long,
      messageSize: Int
  ): F[Unit] = {

    def process(queue: Queue[F, Array[Byte]]) =
      blocker.blockOn(Stream.eval(pull.recv).repeatN(messageCount).through(queue.enqueue).compile.drain)

    val io = for {
      queue  <- Stream.eval(Queue.unbounded[F, Array[Byte]])
      _      <- Stream.resource(process(queue).background)
      result <- queue.dequeue
    } yield result

    for {
      _ <- pull.recv
      _ <- measureTime(io.compile.drain, messageCount, messageSize)
    } yield ()
  }

  private def measureTime[F[_]: Sync: Clock, A](fa: F[A], messageCount: Long, messageSize: Int): F[Unit] =
    for {
      start <- Clock[F].monotonic(TimeUnit.NANOSECONDS)
      _     <- fa
      end   <- Clock[F].monotonic(TimeUnit.NANOSECONDS)

      elapsed    = (end - start) / 1000L
      throughput = (messageCount.toDouble / elapsed.toDouble * 1000000L).toLong
      megabits   = (throughput * messageSize * 8).toDouble / 1000000

      _ <- log(f"message elapsed: ${elapsed.toDouble / 1000000L}%.3f")
      _ <- log(s"message size: ${messageSize.toString} [B]")
      _ <- log(s"message count: ${messageCount.toString}")
      _ <- log(s"mean throughput: ${throughput.toString} [msg/s]")
      _ <- log(f"mean throughput: $megabits%.3f [Mb/s]")
    } yield ()

  private def log[F[_]: Sync](message: => String): F[Unit] =
    Sync[F].delay(println(message))

}
