package io.fmq.pattern

import cats.effect.{Blocker, IO, Resource, Timer}
import cats.syntax.flatMap._
import io.fmq.Context
import io.fmq.frame.Frame
import io.fmq.socket.reqrep.{Reply, Request}
import io.fmq.syntax.literals._
import weaver.{Expectations, IOSuite}

import scala.concurrent.duration._

object RequestReplySpec extends IOSuite {

  override type Res = Pair[IO]

  override def sharedResource: Resource[IO, Pair[IO]] = {
    val uri = tcp"://localhost:53123"

    for {
      blocker <- Blocker[IO]
      ctx     <- Context.create[IO](1, blocker)
      reply   <- Resource.suspend(ctx.createReply.map(_.bind(uri)))
      request <- Resource.suspend(ctx.createRequest.map(_.connect(reply.uri)))
    } yield Pair(request, reply)
  }

  test("process requests one by one") { pair =>
    val Pair(request, reply) = pair

    val server = reply
      .receiveFrame[String]
      .flatMap {
        case Frame.Single(value)   => reply.sendFrame(Frame.Single(value.reverse))
        case Frame.Multipart(_, _) => reply.sendFrame(Frame.Single("multipart-response"))
      }
      .foreverM

    def program(dispatcher: RequestReply[IO]): IO[Expectations] =
      for {
        _         <- Timer[IO].sleep(200.millis)
        response1 <- dispatcher.submit[String, String](Frame.Single("hello"))
        response2 <- dispatcher.submit[String, String](Frame.Multipart("hello", "world"))
      } yield expect(response1 == Frame.Single("olleh")) and expect(response2 == Frame.Single("multipart-response"))

    (for {
      _          <- server.background
      blocker    <- Blocker[IO]
      dispatcher <- RequestReply.create[IO](blocker, request, 10)
    } yield dispatcher).use(program)
  }

  final case class Pair[F[_]](
      request: Request.Socket[F],
      reply: Reply.Socket[F]
  )

}
