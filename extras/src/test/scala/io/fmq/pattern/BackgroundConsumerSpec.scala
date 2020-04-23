package io.fmq.pattern

import cats.effect.{Blocker, IO, Resource, Timer}
import io.fmq.{Context, IOSpec}
import io.fmq.frame.Frame
import io.fmq.socket.{ConsumerSocket, ProducerSocket}
import io.fmq.socket.pubsub.Subscriber
import io.fmq.pattern.BackgroundConsumerSpec.Pair
import io.fmq.syntax.literals._
import org.scalatest.Assertion

import scala.concurrent.duration._

class BackgroundConsumerSpec extends IOSpec {

  "BackgroundConsumer" should {

    "consume messages" in withSockets { pair =>
      val Pair(publisher, subscriber) = pair

      def program(blocker: Blocker): IO[Assertion] =
        for {
          _        <- Timer[IO].sleep(200.millis)
          _        <- publisher.send("hello")
          _        <- publisher.send("world")
          messages <- BackgroundConsumer.consume[IO, String](blocker, subscriber, 128).take(2).compile.toList
        } yield messages shouldBe List(Frame.Single("hello"), Frame.Single("world"))

      Blocker[IO].use(program)
    }

  }

  private def withSockets[A](fa: Pair[IO] => IO[A]): A =
    withContext() { ctx: Context[IO] =>
      val uri = tcp_i"://localhost"

      (for {
        publisher  <- Resource.suspend(ctx.createPublisher.map(_.bindToRandomPort(uri)))
        subscriber <- Resource.suspend(ctx.createSubscriber(Subscriber.Topic.All).map(_.connect(publisher.uri)))
      } yield Pair(publisher, subscriber)).use(fa)
    }

}

object BackgroundConsumerSpec {

  final case class Pair[F[_]](
      publisher: ProducerSocket[F],
      subscriber: ConsumerSocket[F]
  )

}