package io.fmq

import cats.effect.{Blocker, IO}
import cats.syntax.functor._
import weaver.SimpleIOSuite

import scala.concurrent.duration._

object ContextSpec extends SimpleIOSuite {

  simpleTest("release allocated context") {
    val terminated = Blocker[IO]
      .flatMap(blocker => Context.create[IO](1, blocker))
      .use(ctx => ctx.isClosed.tupleRight(ctx))

    for {
      (isClosed, ctx) <- terminated.timeout(3.seconds)
      isClosedNow <- ctx.isClosed
    } yield expect(!isClosed) and expect(isClosedNow)
  }

}
