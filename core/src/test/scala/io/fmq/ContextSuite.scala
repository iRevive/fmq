package io.fmq

import cats.effect.{IO, Resource}
import weaver.IOSuite

trait ContextSuite extends IOSuite {

  override type Res = Context[IO]

  override def sharedResource: Resource[IO, Context[IO]] =
    Context.create[IO](1)

}
