---
id: subscriber
title: Subscriber
---

The subscriber socket can subscribe to a specific topic and can only receive messages.

## Allocation

The subscriber can be created within the `Context`.     

```scala mdoc:silent
import cats.effect.{Resource, IO}
import io.fmq.Context
import io.fmq.socket.pubsub.Subscriber

val topicSubscriberResource: Resource[IO, Subscriber[IO]] =
  for {
    context    <- Context.create[IO](1)
    subscriber <- Resource.eval(context.createSubscriber(Subscriber.Topic.utf8String("my-topic")))
  } yield subscriber

val allSubscriberResource: Resource[IO, Subscriber[IO]] =
  for {
    context    <- Context.create[IO](1)
    subscriber <- Resource.eval(context.createSubscriber(Subscriber.Topic.All))
  } yield subscriber
```

The `Subscriber[F]` is a valid instance of the socket but it's not connect to the network yet. 
Subscriber can connect to the specific port and host.

```scala mdoc:silent
import io.fmq.syntax.literals._

val connected: Resource[IO, Subscriber.Socket[IO]] = 
  for {
    subscriber <- topicSubscriberResource
    connected  <- subscriber.connect(tcp"://localhost:31234")
  } yield connected
```

Since `subscriber.connect` returns `Resource[F, ConsumerSocket[IO]]` the connection will be released automatically. 

## Configuration

The settings can be changed until the socket is connected:  

```scala mdoc:silent
import io.fmq.options.{ReceiveTimeout, Linger}
import io.fmq.socket.pubsub.Subscriber

def configureSubscriber(subscriber: Subscriber[IO]): IO[Unit] = 
  for {
    _ <- subscriber.setReceiveTimeout(ReceiveTimeout.Infinity)
    _ <- subscriber.setLinger(Linger.Immediately)
  } yield ()
```

## Consume messages

Only connected subscriber can consume messages:

```scala mdoc:silent
import cats.syntax.flatMap._
import fs2.Stream

def consumeSingleMessage(socket: Subscriber.Socket[IO]): IO[String] = 
  socket.receive[String]

def consumeMultipartMessage(socket: Subscriber.Socket[IO]): IO[List[String]] = 
  socket.receiveFrame[String].map(_.parts.toList)

def consumeMultipartMessageManually(socket: Subscriber.Socket[IO]): IO[List[String]] = {
  def receiveMultipart: Stream[IO, String] =
    for {
      s <- Stream.eval(socket.receive[String])
      r <- Stream.eval(socket.hasReceiveMore).ifM(Stream.emit(s) ++ receiveMultipart, Stream.emit(s))
    } yield r

  receiveMultipart.compile.toList
}
```