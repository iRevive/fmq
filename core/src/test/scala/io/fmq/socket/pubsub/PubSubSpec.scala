package io.fmq
package socket
package pubsub

import cats.effect.{IO, Resource, Sync}
import io.fmq.address._
import io.fmq.socket.SocketBehavior.SocketResource
import io.fmq.syntax.literals._

import scala.util.Random

class PubSubSpec extends IOSpec with SocketBehavior {

  "PubSub[IO]" when afterWord("protocol is") {

    "tcp" should {
      behave like socketSpec(tcpSocketResource[IO])
    }

    "inproc" should {
      behave like socketSpec(inprocSocketResource[IO])
    }

  }

  private def tcpSocketResource[F[_]: Sync]: PubSubResource[F] =
    new PubSubResource[F] {

      override def bind(producer: Publisher[F], consumer: Subscriber[F], port: Int): Resource[F, Pair] = {
        val uri = Uri.Complete.TCP(Address.Full("localhost", port))

        for {
          p <- producer.bind(uri)
          c <- consumer.connect(uri)
        } yield SocketResource.Pair(p, c)
      }

      override def bindToRandom(producer: Publisher[F], consumer: Subscriber[F]): Resource[F, Pair] = {
        val uri = tcp_i"://localhost"

        for {
          p <- producer.bindToRandomPort(uri)
          c <- consumer.connect(p.uri)
        } yield SocketResource.Pair(p, c)
      }

      override def expectedRandomUri(port: Int): Uri.Complete =
        Uri.Complete.TCP(Address.Full("localhost", port))

    }

  private def inprocSocketResource[F[_]: Sync]: PubSubResource[F] =
    new PubSubResource[F] {

      override def bind(producer: Publisher[F], consumer: Subscriber[F], port: Int): Resource[F, Pair] = {
        val uri = Uri.Complete.InProc(Address.HostOnly(s"localhost-$port"))

        for {
          p <- producer.bind(uri)
          c <- consumer.connect(uri)
        } yield SocketResource.Pair(p, c)
      }

      override def bindToRandom(producer: Publisher[F], consumer: Subscriber[F]): Resource[F, Pair] = {
        val uri = Uri.Complete.InProc(Address.HostOnly(Random.alphanumeric.take(10).mkString))

        for {
          p <- producer.bind(uri)
          c <- consumer.connect(p.uri)
        } yield SocketResource.Pair(p, c)
      }

      override def expectedRandomUri(port: Int): Uri.Complete =
        Uri.Complete.InProc(Address.HostOnly(s"localhost-$port"))

    }

  abstract class PubSubResource[F[_]] extends SocketResource[F, Publisher[F], Subscriber[F]] {

    override def createProducer(context: Context[F]): F[Publisher[F]] =
      context.createPublisher

    override def createConsumer(context: Context[F]): F[Subscriber[F]] =
      context.createSubscriber(Subscriber.Topic.All)

  }

}