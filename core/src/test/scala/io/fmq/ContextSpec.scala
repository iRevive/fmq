package io.fmq

import cats.effect.IO
import cats.syntax.functor._
import org.scalatest.OptionValues._

import scala.concurrent.duration._

class ContextSpec extends IOSpec {

  "Context" should {

    "release allocated context" in {
      val (isClosed, ctx) = Context
        .create[IO](1)
        .use(ctx => ctx.isClosed.tupleRight(ctx))
        .unsafeRunTimed(3.seconds)
        .value

      isClosed shouldBe false
      ctx.isClosed.unsafeRunSync() shouldBe true
    }

  }

}
