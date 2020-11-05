package io.fmq

import cats.effect.syntax.effect._
import cats.effect.{Effect, IO, Sync, Timer}
import org.scalatest.OptionValues._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait IOSpec extends AnyWordSpecLike with Matchers {

  protected implicit val timer: Timer[IO]               = IO.timer(ExecutionContext.global)
  protected implicit val contextShift[IO] = IO.contextShift(ExecutionContext.global)

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  protected def withContext[F[_]: Sync: Effect, A](
      timeout: FiniteDuration = 3.seconds
  )(fa: Context[F] => F[A]): A =
    Blocker[F]
      .flatMap(blocker => Context.create[F](1, blocker))
      .use(fa)
      .toIO
      .unsafeRunTimed(timeout)
      .value

}
