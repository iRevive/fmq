package io.fmq.socket.reqrep

import cats.effect.{IO, Resource, Timer}
import cats.syntax.flatMap._
import io.fmq.{Context, IOSpec}
import io.fmq.frame.Frame
import io.fmq.options.{Identity, RouterHandover, RouterMandatory}
import io.fmq.socket.SocketBehavior
import io.fmq.socket.reqrep.RouterSpec.Pair
import io.fmq.syntax.literals._

import scala.concurrent.duration._

/**
  * Tests are using Timer[IO].sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
class RouterSpec extends IOSpec with SocketBehavior {

  "Router" should {

    "route messages by identity" in withSockets(Identity.utf8String("1")) { pair =>
      val Pair(router, dealer, _) = pair

      for {
        _         <- Timer[IO].sleep(200.millis)
        _         <- dealer.identity
        _         <- dealer.send("Hello")
        request   <- router.receiveFrame[String]
        _         <- router.sendFrame(Frame.Multipart("1", "World-1"))
        _         <- router.sendFrame(Frame.Multipart("2", "World-2"))
        response1 <- dealer.receiveFrame[String]
        _         <- Timer[IO].sleep(100.millis)
        response2 <- dealer.receiveNoWait[String]
      } yield {
        request shouldBe Frame.Multipart("1", "Hello")
        response1 shouldBe Frame.Single("World-1")
        response2 shouldBe empty
      }
    }

    "Handover. disconnect socket with existing identity" in withSockets(Identity.utf8String("ID")) { pair =>
      val Pair(router, dealer, context) = pair

      val dealer2Resource = context.createDealer
        .flatTap(_.setIdentity(Identity.utf8String("ID")))
        .map(_.connect(router.uri))

      val test =
        Resource.suspend(dealer2Resource).use { dealer2: Dealer.Socket[IO] =>
          // We have new peer which should take over, however we are still reading a message
          for {
            message1  <- router.receiveFrame[String]
            _         <- dealer2.sendFrame(Frame.Multipart("Hello", "World"))
            message2  <- router.receiveFrame[String]
            _         <- router.sendFrame(Frame.Multipart("ID", "Response"))
            _         <- Timer[IO].sleep(200.millis)
            response1 <- dealer.receiveNoWait[String]
            response2 <- dealer2.receiveFrame[String]
          } yield {
            message1 shouldBe Frame.Multipart("Hello", "World")
            message2 shouldBe Frame.Multipart("ID", "Hello", "World")
            response1 shouldBe empty
            response2 shouldBe Frame.Single("Response")
          }
        }

      for {
        _        <- router.setHandover(RouterHandover.Handover)
        _        <- Timer[IO].sleep(200.millis)
        _        <- dealer.sendFrame(Frame.Multipart("Hello", "World"))
        identity <- router.receive[String]
        _        <- IO.delay(identity shouldBe "ID")
        _        <- test
      } yield ()
    }

  }

  private def withSockets[A](identity: Identity)(fa: Pair[IO] => IO[A]): A =
    withContext() { ctx: Context[IO] =>
      val uri = tcp_i"://localhost"

      (for {
        router <- Resource.liftF(ctx.createRouter)
        dealer <- Resource.liftF(ctx.createDealer)
        _      <- Resource.liftF(router.setMandatory(RouterMandatory.NonMandatory))
        _      <- Resource.liftF(dealer.setIdentity(identity))
        r      <- router.bindToRandomPort(uri)
        d      <- dealer.connect(r.uri)
      } yield Pair(r, d, ctx)).use(fa)
    }

}

object RouterSpec {

  final case class Pair[F[_]](
      router: Router.Socket[F],
      dealer: Dealer.Socket[F],
      context: Context[F]
  )

}
