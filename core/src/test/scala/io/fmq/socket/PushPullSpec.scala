package io.fmq
package socket

import cats.effect.{IO, Resource, Sync}
import io.fmq.domain.{Port, Protocol}
import io.fmq.socket.SocketBehavior.SocketResource

class PushPullSpec extends IOSpec with SocketBehavior {

  "PushPull[IO]" should {
    behave like socketSpec[IO](mkSocketResource[IO])
  }

  private def mkSocketResource[F[_]: Sync]: SocketResource[F, F] =
    new SocketResource[F, F] {

      override def bind(context: Context[F], port: Port): Resource[F, SocketResource.Pair[F]] = {
        val address = Protocol.tcp("localhost", port)

        for {
          pull     <- context.createPull
          push     <- context.createPush
          consumer <- pull.bind(address)
          producer <- push.connect(address)
        } yield SocketResource.Pair(producer, consumer)
      }

      override def bindToRandomPort(context: Context[F]): Resource[F, SocketResource.Pair[F]] = {
        val address = Protocol.tcp("localhost")

        for {
          pull     <- context.createPull
          push     <- context.createPush
          consumer <- pull.bindToRandomPort(address)
          producer <- push.connect(Protocol.tcp("localhost", consumer.port))
        } yield SocketResource.Pair(producer, consumer)
      }

    }

}
