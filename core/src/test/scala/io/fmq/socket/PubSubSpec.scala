package io.fmq
package socket

import cats.effect.{IO, Resource, Sync}
import io.fmq.domain.{Port, Protocol, SubscribeTopic}
import io.fmq.socket.SocketBehavior.SocketResource

class PubSubSpec extends IOSpec with SocketBehavior {

  "PubSub[IO]" should {
    behave like socketSpec[IO](mkSocketResource[IO])
  }

  private def mkSocketResource[F[_]: Sync]: SocketResource[F, F] =
    new SocketResource[F, F] {

      override def bind(context: Context[F], port: Port): Resource[F, SocketResource.Pair[F]] = {
        val address = Protocol.tcp("localhost", port)
        val topic   = SubscribeTopic.All

        for {
          pub      <- context.createPublisher
          sub      <- context.createSubscriber(topic)
          producer <- pub.bind(address)
          consumer <- sub.connect(address)
        } yield SocketResource.Pair(producer, consumer)
      }

      override def bindToRandomPort(context: Context[F]): Resource[F, SocketResource.Pair[F]] = {
        val address = Protocol.tcp("localhost")
        val topic   = SubscribeTopic.All

        for {
          pub      <- context.createPublisher
          sub      <- context.createSubscriber(topic)
          producer <- pub.bindToRandomPort(address)
          consumer <- sub.connect(Protocol.tcp("localhost", producer.port))
        } yield SocketResource.Pair(producer, consumer)
      }

    }

}
