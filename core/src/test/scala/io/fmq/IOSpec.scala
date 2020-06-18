package io.fmq

import cats.effect.{Blocker, IO, Resource}
import weaver.IOSuite

trait IOSpec extends IOSuite {

  override type Res = Context[IO]

  override def sharedResource: Resource[IO, Context[IO]] =
    for {
      blocker <- Blocker[IO]
      ctx <- Context.create[IO](1, blocker)
    } yield ctx

}
