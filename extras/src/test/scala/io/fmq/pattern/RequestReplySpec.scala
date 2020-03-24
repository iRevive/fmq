package io.fmq.pattern

import cats.effect.{Blocker, IO}
import cats.syntax.flatMap._
import io.fmq.address.{Address, Host, Port, Uri}
import io.fmq.frame.Frame
import io.fmq.socket.reqrep.{ReplySocket, RequestSocket}
import io.fmq.pattern.RequestReplySpec.Pair
import io.fmq.{Context, IOSpec}
import org.scalatest.Assertion

class RequestReplySpec extends IOSpec {

  "RequestReply" should {

    "process requests one by one" in withSockets { pair =>
      val Pair(request, reply) = pair

      val server = reply
        .receiveFrame[String]
        .flatMap {
          case Frame.Single(value)   => reply.sendFrame(Frame.Single(value.reverse))
          case Frame.Multipart(_, _) => reply.sendFrame(Frame.Single("multipart-response"))
        }
        .foreverM

      def program(dispatcher: RequestReply[IO]): IO[Assertion] =
        for {
          response1 <- dispatcher.submit[String, String](Frame.Single("hello"))
          response2 <- dispatcher.submit[String, String](Frame.Multipart("hello", "world"))
        } yield {
          response1 shouldBe Frame.Single("olleh")
          response2 shouldBe Frame.Single("multipart-response")
        }

      (for {
        _          <- server.background
        blocker    <- Blocker[IO]
        dispatcher <- RequestReply.create[IO](blocker, request, 10)
      } yield dispatcher).use(program)
    }

  }

  private def withSockets[A](fa: Pair[IO] => IO[A]): A =
    withContext() { ctx: Context[IO] =>
      val uri = Uri.Complete.TCP(Address.Full(Host.Fixed("localhost"), Port(53123)))

      (for {
        req     <- ctx.createRequest
        rep     <- ctx.createReply
        reply   <- rep.bind(uri)
        request <- req.connect(reply.uri)
      } yield Pair(request, reply)).use(fa)
    }

}

object RequestReplySpec {

  final case class Pair[F[_]](
      request: RequestSocket[F],
      reply: ReplySocket[F]
  )

}
