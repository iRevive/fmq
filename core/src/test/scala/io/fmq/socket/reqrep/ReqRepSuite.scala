package io.fmq
package socket
package reqrep

import cats.effect.{IO, Resource}
import cats.syntax.either._
import io.fmq.frame.Frame
import io.fmq.syntax.literals._

import scala.concurrent.duration._

/**
  * Tests are using IO.sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
object ReqRepSuite extends ContextSuite with SocketBehavior {

  test("simple req rep") { ctx =>
    withSockets(ctx) { case Pair(req, rep) =>
      for {
        _        <- IO.sleep(200.millis)
        _        <- req.send("Hi")
        request  <- rep.receive[String]
        _        <- rep.send("Hi2")
        response <- req.receive[String]
      } yield expect(request == "Hi") and expect(response == "Hi2")
    }
  }

  test("multipart req rep") { ctx =>
    withSockets(ctx) { case Pair(req, rep) =>
      for {
        _        <- IO.sleep(200.millis)
        _        <- req.sendFrame(Frame.Multipart("Hello", "World"))
        request  <- rep.receiveFrame[String]
        _        <- rep.sendFrame(Frame.Multipart("Hello", "Back"))
        response <- req.receiveFrame[String]
      } yield expect(request == Frame.Multipart("Hello", "World")) and expect(response == Frame.Multipart("Hello", "Back"))
    }
  }

  test("fail when sending two requests in a row") { ctx =>
    withSockets(ctx) { case Pair(req, _) =>
      for {
        _      <- IO.sleep(200.millis)
        _      <- req.send("Hi")
        result <- req.send("Hi2").attempt
      } yield expect(result.leftMap(_.getMessage) == Left("Errno 156384763"))
    }
  }

  test("fail when receiving message before sending") { ctx =>
    withSockets(ctx) { case Pair(req, _) =>
      for {
        _      <- IO.sleep(200.millis)
        result <- req.receive[Array[Byte]].attempt
      } yield expect(result.leftMap(_.getMessage) == Left("Errno 156384763"))
    }

  }

  test("fail when sending message in response before receiving") { ctx =>
    withSockets(ctx) { case Pair(_, rep) =>
      for {
        _      <- IO.sleep(200.millis)
        result <- rep.send("hi").attempt
      } yield expect(result.leftMap(_.getMessage) == Left("Errno 156384763"))
    }
  }

  private def withSockets[A](ctx: Context[IO])(fa: Pair[IO] => IO[A]): IO[A] = {
    val uri = tcp_i"://localhost"

    (for {
      reply   <- Resource.suspend(ctx.createReply.map(_.bindToRandomPort(uri)))
      request <- Resource.suspend(ctx.createRequest.map(_.connect(reply.uri)))
    } yield Pair(request, reply)).use(fa)
  }

  private final case class Pair[F[_]](
      request: Request.Socket[F],
      reply: Reply.Socket[F]
  )

}
