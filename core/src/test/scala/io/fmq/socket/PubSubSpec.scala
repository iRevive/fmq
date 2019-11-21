package io.fmq
package socket

import cats.Applicative
import cats.effect.{IO, Resource, Sync}
import io.fmq.domain.{Port, Protocol, SubscribeTopic}
import io.fmq.free.ConnectionIO
import io.fmq.socket.SocketBehavior.SocketResource

class PubSubSpec extends IOSpec with SocketBehavior {

  "PubSub[IO, IO]" should {
    behave like socketSpec[IO](mkSocketResource[IO, IO])
  }

  "PubSub[IO, ConnectionIO]" should {
    behave like socketSpec[ConnectionIO](mkSocketResource[IO, ConnectionIO])
  }

  private def mkSocketResource[F[_]: Applicative, H[_]: Sync]: SocketResource[F, H] =
    new SocketResource[F, H] {

      override def bind(context: Context[F], port: Port): Resource[F, SocketResource.Pair[H]] = {
        val address = Protocol.tcp("localhost", port)
        val topic   = SubscribeTopic.All

        for {
          pub      <- context.createPublisher[H]
          sub      <- context.createSubscriber[H](topic)
          producer <- pub.bind(address)
          consumer <- sub.connect(address)
        } yield SocketResource.Pair(producer, consumer)
      }

      override def bindToRandomPort(context: Context[F]): Resource[F, SocketResource.Pair[H]] = {
        val address = Protocol.tcp("localhost")
        val topic   = SubscribeTopic.All

        for {
          pub      <- context.createPublisher[H]
          sub      <- context.createSubscriber[H](topic)
          producer <- pub.bindToRandomPort(address)
          consumer <- sub.connect(Protocol.tcp("localhost", producer.port))
        } yield SocketResource.Pair(producer, consumer)
      }

    }

}
