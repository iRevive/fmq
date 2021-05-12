package io.fmq.pattern

import java.util.concurrent.Executors

import cats.effect.{IO, Resource}
import io.fmq.Context
import io.fmq.frame.Frame
import io.fmq.socket.reqrep.{Reply, Request}
import io.fmq.syntax.literals._
import weaver.{Expectations, IOSuite}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object RequestReplySuite extends IOSuite {

  type Res = (Request.Socket[IO], Reply.Socket[IO])

  test("process requests one by one") { pair =>
    val (request, reply) = pair

    val server = reply
      .receiveFrame[String]
      .flatMap {
        case Frame.Single(value)   => reply.sendFrame(Frame.Single(value.reverse))
        case Frame.Multipart(_, _) => reply.sendFrame(Frame.Single("multipart-response"))
      }
      .foreverM

    def program(dispatcher: RequestReply[IO]): IO[Expectations] =
      for {
        _         <- IO.sleep(200.millis)
        response1 <- dispatcher.submit[String, String](Frame.Single("hello"))
        response2 <- dispatcher.submit[String, String](Frame.Multipart("hello", "world"))
      } yield expect(response1 == Frame.Single("olleh")) and expect(response2 == Frame.Single("multipart-response"))

    (for {
      _          <- server.background
      ec         <- Resource.make(IO.delay(Executors.newCachedThreadPool()))(e => IO.delay(e.shutdown()))
      dispatcher <- RequestReply.create[IO](ExecutionContext.fromExecutor(ec), request, 10)
    } yield dispatcher).use(program)
  }

  override def sharedResource: Resource[IO, Res] = {
    val uri = tcp"://localhost:53123"

    for {
      ctx     <- Context.create[IO](1)
      reply   <- Resource.suspend(ctx.createReply.map(_.bind(uri)))
      request <- Resource.suspend(ctx.createRequest.map(_.connect(reply.uri)))
    } yield (request, reply)
  }

}
