package io.fmq
package socket
package reqrep

import cats.effect.{IO, Resource}
import cats.syntax.either._
import io.fmq.frame.Frame
import io.fmq.socket.reqrep.ReqRepSpec.Pair
import io.fmq.syntax.literals._

import scala.concurrent.duration._

/**
  * Tests are using IO.sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
class ReqRepSpec extends IOSpec with SocketBehavior {

  "ReqRep" should {

    "simple req rep" in withSockets { pair =>
      val Pair(req, rep) = pair

      for {
        _        <- IO.sleep(200.millis)
        _        <- req.send("Hi")
        request  <- rep.receive[String]
        _        <- rep.send("Hi2")
        response <- req.receive[String]
      } yield {
        request shouldBe "Hi"
        response shouldBe "Hi2"
      }
    }

    "multipart req rep" in withSockets { pair =>
      val Pair(req, rep) = pair

      for {
        _        <- IO.sleep(200.millis)
        _        <- req.sendFrame(Frame.Multipart("Hello", "World"))
        request  <- rep.receiveFrame[String]
        _        <- rep.sendFrame(Frame.Multipart("Hello", "Back"))
        response <- req.receiveFrame[String]
      } yield {
        request shouldBe Frame.Multipart("Hello", "World")
        response shouldBe Frame.Multipart("Hello", "Back")
      }

    }

    "fail" when {

      "sending two requests in a row" in withSockets { pair =>
        val Pair(req, _) = pair

        for {
          _      <- IO.sleep(200.millis)
          _      <- req.send("Hi")
          result <- req.send("Hi2").attempt
        } yield result.leftMap(_.getMessage) shouldBe Left("Errno 156384763")
      }

      "receiving message before sending" in withSockets { pair =>
        val Pair(req, _) = pair

        for {
          _      <- IO.sleep(200.millis)
          result <- req.receive[Array[Byte]].attempt
        } yield result.leftMap(_.getMessage) shouldBe Left("Errno 156384763")
      }

      "sending message in response before receiving" in withSockets { pair =>
        val Pair(_, rep) = pair

        for {
          _      <- IO.sleep(200.millis)
          result <- rep.send("hi").attempt
        } yield result.leftMap(_.getMessage) shouldBe Left("Errno 156384763")
      }

    }

  }

  private def withSockets[A](fa: Pair[IO] => IO[A]): A =
    withContext() { ctx: Context[IO] =>
      val uri = tcp_i"://localhost"

      (for {
        reply   <- Resource.suspend(ctx.createReply.map(_.bindToRandomPort(uri)))
        request <- Resource.suspend(ctx.createRequest.map(_.connect(reply.uri)))
      } yield Pair(request, reply)).use(fa)
    }

}

object ReqRepSpec {

  final case class Pair[F[_]](
      request: Request.Socket[F],
      reply: Reply.Socket[F]
  )

}
