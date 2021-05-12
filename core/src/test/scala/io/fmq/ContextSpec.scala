package io.fmq

import cats.effect.IO
import cats.syntax.functor._
import weaver.SimpleIOSuite

import scala.concurrent.duration._

object ContextSpec extends SimpleIOSuite {

  test("release allocated context") {
    val terminated = Context
      .create[IO](1)
      .use(ctx => ctx.isClosed.tupleRight(ctx))

    for {
      (isClosed, ctx) <- terminated.timeout(3.seconds)
      isClosedNow     <- ctx.isClosed
    } yield expect(!isClosed) and expect(isClosedNow)
  }

}
