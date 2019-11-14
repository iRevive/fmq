package io.fmq

import cats.effect.{Blocker, ContextShift, IO, Resource, Timer}
import org.scalatest.{Matchers, OptionValues, WordSpecLike}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait IOSpec extends WordSpecLike with Matchers with OptionValues {

  protected implicit val timer: Timer[IO]               = IO.timer(ExecutionContext.global)
  protected implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  protected def withContext[A](timeout: FiniteDuration = 3.seconds)(fa: Context[IO] => Resource[IO, A]): A =
    (for {
      blocker <- Blocker[IO]
      ctx     <- Context.create[IO](1, blocker)
      r       <- fa(ctx)
    } yield r).use(v => IO.pure(v)).unsafeRunTimed(timeout).value

}
