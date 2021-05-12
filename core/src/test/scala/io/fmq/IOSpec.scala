package io.fmq

import cats.effect.unsafe.IORuntime
import cats.effect.{IO, Resource}
import weaver.IOSuite

trait IOSpec extends IOSuite {

  override type Res = Context[IO]

  protected implicit val runtime: IORuntime = IORuntime.global

  override def sharedResource: Resource[IO, Context[IO]] =
    Context.create[IO](1)

}
