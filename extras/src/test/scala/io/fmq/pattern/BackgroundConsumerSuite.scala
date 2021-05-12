package io.fmq.pattern

import java.util.concurrent.Executors

import cats.effect.{IO, Resource}
import io.fmq.Context
import io.fmq.frame.Frame
import io.fmq.socket.pubsub.Subscriber
import io.fmq.socket.{ConsumerSocket, ProducerSocket}
import io.fmq.syntax.literals._
import weaver.{Expectations, IOSuite}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object BackgroundConsumerSuite extends IOSuite {

  type Res = (ProducerSocket[IO], ConsumerSocket[IO])

  test("consume messages") { pair =>
    val (publisher, subscriber) = pair

    def program(blocker: ExecutionContext): IO[Expectations] =
      for {
        _        <- IO.sleep(200.millis)
        _        <- publisher.send("hello")
        _        <- publisher.send("world")
        messages <- BackgroundConsumer.consume[IO, String](blocker, subscriber, 128).take(2).compile.toList
      } yield expect(messages == List(Frame.Single("hello"), Frame.Single("world")))

    Resource
      .make(IO.delay(Executors.newCachedThreadPool()))(e => IO.delay(e.shutdown()))
      .map(ExecutionContext.fromExecutor)
      .use(program)
  }

  override def sharedResource: Resource[IO, Res] = {
    val uri = tcp_i"://localhost"

    for {
      ctx        <- Context.create[IO](1)
      publisher  <- Resource.suspend(ctx.createPublisher.map(_.bindToRandomPort(uri)))
      subscriber <- Resource.suspend(ctx.createSubscriber(Subscriber.Topic.All).map(_.connect(publisher.uri)))
    } yield (publisher, subscriber)
  }

}
