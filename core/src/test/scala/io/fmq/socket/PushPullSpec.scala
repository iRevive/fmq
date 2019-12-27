package io.fmq
package socket

import cats.effect.{IO, Resource, Sync}
import io.fmq.domain.{Port, Protocol}
import io.fmq.socket.SocketBehavior.SocketResource

class PushPullSpec extends IOSpec with SocketBehavior {

  "PushPull[IO]" should {
    behave like socketSpec[IO, Push[IO], Pull[IO]](mkSocketResource[IO])
  }

  private def mkSocketResource[F[_]: Sync]: SocketResource[F, F, Push[F], Pull[F]] =
    new SocketResource[F, F, Push[F], Pull[F]] {

      override def createProducer(context: Context[F]): Resource[F, Push[F]] =
        context.createPush

      override def createConsumer(context: Context[F]): Resource[F, Pull[F]] =
        context.createPull

      override def bind(push: Push[F], pull: Pull[F], port: Port): Resource[F, SocketResource.Pair[F]] = {
        val address = Protocol.tcp("localhost", port)

        for {
          consumer <- pull.bind(address)
          producer <- push.connect(address)
        } yield SocketResource.Pair(producer, consumer)
      }

      override def bindToRandomPort(push: Push[F], pull: Pull[F]): Resource[F, SocketResource.Pair[F]] = {
        val address = Protocol.tcp("localhost")

        for {
          consumer <- pull.bindToRandomPort(address)
          producer <- push.connect(Protocol.tcp("localhost", consumer.port))
        } yield SocketResource.Pair(producer, consumer)
      }

    }

}
