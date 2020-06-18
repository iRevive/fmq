package io.fmq.pattern

import cats.effect.{Blocker, IO, Resource, Timer}
import io.fmq.Context
import io.fmq.frame.Frame
import io.fmq.socket.pubsub.Subscriber
import io.fmq.socket.{ConsumerSocket, ProducerSocket}
import io.fmq.syntax.literals._
import weaver.IOSuite

import scala.concurrent.duration._

object BackgroundConsumerSpec extends IOSuite {

  override type Res = Pair[IO]

  override def sharedResource: Resource[IO, Pair[IO]] = {
    val uri = tcp_i"://localhost"

    for {
      blocker    <- Blocker[IO]
      ctx        <- Context.create[IO](1, blocker)
      publisher  <- Resource.suspend(ctx.createPublisher.map(_.bindToRandomPort(uri)))
      subscriber <- Resource.suspend(ctx.createSubscriber(Subscriber.Topic.All).map(_.connect(publisher.uri)))
    } yield Pair(publisher, subscriber)
  }

  test("consume messages") { pair =>
    val Pair(publisher, subscriber) = pair

    for {
      _ <- Timer[IO].sleep(200.millis)
      _ <- publisher.send("hello")
      _ <- publisher.send("world")
      messages <- Blocker[IO].use(
        BackgroundConsumer.consume[IO, String](_, subscriber, 128).take(2).compile.toList
      )
    } yield expect(messages == List(Frame.Single("hello"), Frame.Single("world")))
  }

  final case class Pair[F[_]](
      publisher: ProducerSocket[F],
      subscriber: ConsumerSocket[F]
  )

}
