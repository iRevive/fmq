package io.fmq
package socket

import cats.Applicative
import cats.effect.{IO, Resource, Sync}
import io.fmq.domain.{Port, Protocol}
import io.fmq.free.ConnectionIO
import io.fmq.socket.SocketBehavior.SocketResource

class PushPullSpec extends IOSpec with SocketBehavior {

  "PushPull[IO, IO]" should {
    behave like socketSpec[IO](mkSocketResource[IO, IO])
  }

  "PushPull[IO, ConnectionIO]" should {
    behave like socketSpec[ConnectionIO](mkSocketResource[IO, ConnectionIO])
  }

  private def mkSocketResource[F[_]: Applicative, H[_]: Sync]: SocketResource[F, H] =
    new SocketResource[F, H] {

      override def bind(context: Context[F], port: Port): Resource[F, SocketResource.Pair[H]] = {
        val address = Protocol.tcp("localhost", port)

        for {
          pull     <- context.createPull[H]
          push     <- context.createPush[H]
          consumer <- pull.bind(address)
          producer <- push.connect(address)
        } yield SocketResource.Pair(producer, consumer)
      }

      override def bindToRandomPort(context: Context[F]): Resource[F, SocketResource.Pair[H]] = {
        val address = Protocol.tcp("localhost")

        for {
          pull     <- context.createPull[H]
          push     <- context.createPush[H]
          consumer <- pull.bindToRandomPort(address)
          producer <- push.connect(Protocol.tcp("localhost", consumer.port))
        } yield SocketResource.Pair(producer, consumer)
      }

    }

}
