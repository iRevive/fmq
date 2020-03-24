package io.fmq
package socket
package pipeline

import cats.effect.{IO, Resource, Sync}
import io.fmq.address._
import io.fmq.socket.SocketBehavior.SocketResource

import scala.util.Random

class PushPullSpec extends IOSpec with SocketBehavior {

  "PushPull[IO]" when afterWord("protocol is") {

    "tcp" should {
      behave like socketSpec(tcpSocketResource[IO])
    }

    "inproc" should {
      behave like socketSpec(inprocSocketResource[IO])
    }

  }

  private def tcpSocketResource[F[_]: Sync]: PushPullResource[F, Protocol.TCP, Address.Full] =
    new PushPullResource[F, Protocol.TCP, Address.Full] {

      override def bind(push: Push[F], pull: Pull[F], port: Port): Resource[F, Pair] = {
        val uri = Uri.tcp(Address.Full(Host.Fixed("localhost"), port))

        for {
          consumer <- pull.bind(uri)
          producer <- push.connect(uri)
        } yield SocketResource.Pair(producer, consumer)
      }

      override def bindToRandom(push: Push[F], pull: Pull[F]): Resource[F, Pair] = {
        val uri = Uri.tcp(Address.HostOnly(Host.Fixed("localhost")))

        for {
          consumer <- pull.bindToRandomPort(uri)
          producer <- push.connect(consumer.uri)
        } yield SocketResource.Pair(producer, consumer)
      }

      override def expectedRandomUri(port: Port): Uri[Protocol.TCP, Address.Full] =
        Uri.tcp(Address.Full(Host.Fixed("localhost"), port))

    }

  private def inprocSocketResource[F[_]: Sync]: PushPullResource[F, Protocol.InProc, Address.HostOnly] =
    new PushPullResource[F, Protocol.InProc, Address.HostOnly] {

      override def bind(push: Push[F], pull: Pull[F], port: Port): Resource[F, Pair] = {
        val uri = Uri.inproc(Address.HostOnly(Host.Fixed(s"localhost-$port")))

        for {
          consumer <- pull.bind(uri)
          producer <- push.connect(uri)
        } yield SocketResource.Pair(producer, consumer)
      }

      override def bindToRandom(push: Push[F], pull: Pull[F]): Resource[F, Pair] = {
        val uri = Uri.inproc(Address.HostOnly(Host.Fixed(Random.alphanumeric.take(10).mkString)))

        for {
          consumer <- pull.bind(uri)
          producer <- push.connect(consumer.uri)
        } yield SocketResource.Pair(producer, consumer)
      }

      override def expectedRandomUri(port: Port): Uri[Protocol.InProc, Address.HostOnly] =
        Uri.inproc(Address.HostOnly(Host.Fixed(s"localhost-$port")))

    }

  abstract class PushPullResource[F[_], P <: Protocol, A <: Address] extends SocketResource[F, P, A, Push[F], Pull[F]] {

    override def createProducer(context: Context[F]): Resource[F, Push[F]] =
      context.createPush

    override def createConsumer(context: Context[F]): Resource[F, Pull[F]] =
      context.createPull

  }

}
