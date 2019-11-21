package io.fmq

import cats.effect.{Blocker, ContextShift, IO, Sync, Timer}
import org.scalatest.{Matchers, OptionValues, WordSpecLike}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait IOSpec extends WordSpecLike with Matchers with OptionValues {

  protected implicit val timer: Timer[IO]               = IO.timer(ExecutionContext.global)
  protected implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  protected def withContext[F[_]: Sync: ContextShift: ToIO, A](timeout: FiniteDuration = 3.seconds)(fa: Context[F] => F[A]): A = {
    val io = Blocker[F].flatMap(blocker => Context.create[F](1, blocker)).use(fa)
    ToIO[F].apply(io).unsafeRunTimed(timeout).value
  }

}
