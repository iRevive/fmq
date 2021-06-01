package io.fmq

import java.util.concurrent.Executors

import cats.effect.{IO, Resource}
import weaver.IOSuite

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

trait ContextSuite extends IOSuite {

  override type Res = Context[IO]

  override def sharedResource: Resource[IO, Context[IO]] =
    Context.create[IO](1)

  protected final def singleThreadExecutionContext: Resource[IO, ExecutionContextExecutor] =
    Resource
      .make(IO.delay(Executors.newSingleThreadExecutor()))(ec => IO.blocking(ec.shutdown()))
      .map(ExecutionContext.fromExecutor)

}
