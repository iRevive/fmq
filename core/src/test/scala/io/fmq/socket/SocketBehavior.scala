package io.fmq.socket

import cats.effect.{IO, Resource}
import cats.syntax.flatMap._
import io.fmq.domain.{Identity, Linger, ReceiveTimeout, SendTimeout}
import io.fmq.socket.SocketBehavior.MkSocket
import io.fmq.socket.internal.SocketApi
import io.fmq.{Context, IOSpec}
import org.scalatest.Inside

import scala.concurrent.duration._

trait SocketBehavior extends Inside {
  self: IOSpec =>

  private[fmq] def supportedOperations[A <: SocketApi[IO]](mkSocket: MkSocket[A]): Unit = {

    "operate receiveTimeout" in withSocket { socket =>
      def changeTimeout(timeout: ReceiveTimeout): IO[ReceiveTimeout] =
        socket.setReceiveTimeout(timeout) >> socket.receiveTimeout

      for {
        timeout1 <- changeTimeout(ReceiveTimeout.NoDelay)
        timeout2 <- changeTimeout(ReceiveTimeout.WaitUntilAvailable)
        timeout3 <- changeTimeout(ReceiveTimeout.FixedTimeout(5.seconds))
      } yield {
        timeout1 shouldBe ReceiveTimeout.NoDelay
        timeout2 shouldBe ReceiveTimeout.WaitUntilAvailable
        timeout3 shouldBe ReceiveTimeout.FixedTimeout(5.seconds)
      }
    }

    "operate sendTimeout" in withSocket { socket =>
      def changeTimeout(timeout: SendTimeout): IO[SendTimeout] =
        socket.setSendTimeout(timeout) >> socket.sendTimeout

      for {
        timeout1 <- changeTimeout(SendTimeout.NoDelay)
        timeout2 <- changeTimeout(SendTimeout.Infinity)
        timeout3 <- changeTimeout(SendTimeout.FixedTimeout(5.seconds))
      } yield {
        timeout1 shouldBe SendTimeout.NoDelay
        timeout2 shouldBe SendTimeout.Infinity
        timeout3 shouldBe SendTimeout.FixedTimeout(5.seconds)
      }
    }

    "operate linger" in withSocket { socket =>
      def changeLinger(linger: Linger): IO[Linger] =
        socket.setLinger(linger) >> socket.linger

      for {
        linger1 <- changeLinger(Linger.Immediately)
        linger2 <- changeLinger(Linger.Infinity)
        linger3 <- changeLinger(Linger.FixedTimeout(5.seconds))
      } yield {
        linger1 shouldBe Linger.Immediately
        linger2 shouldBe Linger.Infinity
        linger3 shouldBe Linger.FixedTimeout(5.seconds)
      }
    }

    "operate identity" in withSocket { socket =>
      val identity = Identity.Fixed(Array(1, 2, 3))

      for {
        identity1 <- socket.setIdentity(identity) >> socket.identity
      } yield {
        inside(identity1) {
          case Identity.Fixed(value) => value should not be empty
        }
      }
    }

    def withSocket[B](fa: A => IO[B]): B =
      withContext()(ctx => mkSocket.make(ctx).evalMap(fa))

  }

}

object SocketBehavior {

  trait MkSocket[A <: SocketApi[IO]] {
    def make(context: Context[IO]): Resource[IO, A]
  }

}
