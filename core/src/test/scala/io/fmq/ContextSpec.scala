package io.fmq

import cats.effect.{Blocker, IO}
import cats.syntax.functor._

import scala.concurrent.duration._

class ContextSpec extends IOSpec {

  "Context" should {

    "release allocated context" in {
      val (isClosed, ctx) = Blocker[IO]
        .flatMap(blocker => Context.create[IO](1, blocker))
        .use(ctx => ctx.isClosed.tupleRight(ctx))
        .unsafeRunTimed(3.seconds)
        .value

      isClosed shouldBe false
      ctx.isClosed.unsafeRunSync() shouldBe true
    }

  }

}
