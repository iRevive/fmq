package io.fmq

import java.util.concurrent.TimeUnit

import cats.effect.syntax.concurrent._
import cats.effect.{Blocker, Clock, Concurrent, ContextShift, ExitCode, IO, IOApp, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.~>
import fs2.Stream
import fs2.concurrent.Queue
import io.fmq.domain.{Port, Protocol}
import io.fmq.free.Executor

object LocalThrScala extends IOApp {

  override def run(argv: List[String]): IO[ExitCode] = {
    val messageSize  = argv(1).toInt
    val messageCount = argv(2).toLong
    val protocol     = Protocol.tcp("localhost", Port(5555))

    Blocker[IO]
      .flatMap(blocker => Context.create[IO](1, blocker).tupleRight(blocker))
      .use {
        case (ctx, blocker) =>
          implicit val fk: ToIO[ConnectionIO]     = Executor[IO].executeK(blocker)
          implicit val clock: Clock[ConnectionIO] = Clock.create

          val ioStream = ctx
            .createPull[IO]
            .flatMap(_.bind(protocol))
            .use(socket => measureStream(socket, messageCount, messageSize))

          val ioCycle = ctx
            .createPull[IO]
            .flatMap(_.bind(protocol))
            .use(socket => measureCycle(socket, messageCount, messageSize))

          val ioBackground = ctx
            .createPull[IO]
            .flatMap(_.bind(protocol))
            .use(socket => measureBackground(socket, blocker, messageCount, messageSize))

          val connectionIOStream =
            ctx
              .createPull[ConnectionIO]
              .flatMap(_.bind(protocol))
              .use(socket => fk(measureStream[ConnectionIO](socket, messageCount, messageSize)))

          val connectionIOCycle: IO[Unit] =
            ctx
              .createPull[ConnectionIO]
              .flatMap(_.bind(protocol))
              .use(socket => measureCycle[ConnectionIO](socket, messageCount, messageSize))

          for {
            _ <- log[IO]("Measure fs2.Stream[IO] performance") >> log[IO]("")
            _ <- ioStream
            _ <- log[IO]("Measure IO cycle performance") >> log[IO]("")
            _ <- ioCycle
            _ <- log[IO]("Measure fs2.Stream[IO] background performance") >> log[IO]("")
            _ <- ioBackground

            _ <- log[IO]("Measure fs2.Stream[ConnectionIO] performance") >> log[IO]("")
            _ <- connectionIOStream
            _ <- log[IO]("Measure ConnectionIO cycle performance") >> log[IO]("")
            _ <- connectionIOCycle
          } yield ExitCode.Success
      }

  }

  def measureStream[F[_]: Sync: Clock](pull: ConsumerSocket[F], messageCount: Long, messageSize: Int): F[Unit] = {
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

  def measureCycle[F[_]: Sync: Clock: ToIO](pull: ConsumerSocket[F], messageCount: Long, messageSize: Int): IO[Unit] = {
    val io = IO.delay {
      for (_ <- 0 until messageCount.toInt - 1) {
        implicitly[ToIO[F]].apply(pull.recv).unsafeRunSync()
      }
    }

    for {
      _ <- implicitly[ToIO[F]].apply(pull.recv)
      _ <- measureTime(io, messageCount, messageSize)
    } yield ()
  }

  def measureBackground[F[_]: Concurrent: ContextShift: Clock](
                                           pull: ConsumerSocket[F], blocker: Blocker, messageCount: Long, messageSize: Int
                                         ): F[Unit] = {

    def process(queue: Queue[F, Array[Byte]]) =
      blocker.blockOn(Stream.eval(pull.recv).repeatN(messageCount).through(queue.enqueue).compile.drain)

      val io = for {
      queue  <- Stream.eval(Queue.unbounded[F, Array[Byte]])
      _      <- Stream.resource(Resource.make(process(queue).start)(_.cancel))
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

  private type ToIO[F[_]] = F ~> IO

  private implicit val ioToIO: ToIO[IO] = new ToIO[IO] {
    override def apply[A](fa: IO[A]): IO[A] = fa
  }

}
