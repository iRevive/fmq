package io.fmq

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.scalatest.OptionValues._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

trait IOSpec extends AnyWordSpecLike with Matchers {

  implicit val runtime: IORuntime = IORuntime.global

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  protected def withContext[A](
      timeout: FiniteDuration = 30.seconds
  )(fa: Context[IO] => IO[A]): A =
    Context
      .create[IO](1)
      .use(fa)
      .unsafeRunTimed(timeout)
      .value

}
