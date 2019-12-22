---
layout: docs
title:  "Subscriber"
number: 3
---

# Subscriber

The subscriber socket can subscribe to a specific topic and can only receive messages.

## Allocation

The subscriber can be created within the `Context`.     

```scala mdoc:silent
import cats.effect.{Blocker, ContextShift, Resource, IO}
import io.fmq.Context
import io.fmq.domain.SubscribeTopic
import io.fmq.socket.Subscriber

import scala.concurrent.ExecutionContext

implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

val topicSubscriberResource: Resource[IO, Subscriber[IO]] =
  for {
    blocker    <- Blocker[IO]
    context    <- Context.create[IO](1, blocker)
    subscriber <- context.createSubscriber(SubscribeTopic.utf8String("my-topic"))
  } yield subscriber

val allSubscriberResource: Resource[IO, Subscriber[IO]] =
  for {
    blocker    <- Blocker[IO]
    context    <- Context.create[IO](1, blocker)
    subscriber <- context.createSubscriber(SubscribeTopic.All)
  } yield subscriber
```

The `Subscriber[F]` is a valid instance of the socket but it's not connect to the network yet. 
Subscriber can connect to the specific port and host.

```scala mdoc:silent
import io.fmq.domain.{Protocol, Port}
import io.fmq.ConsumerSocket

val connected: Resource[IO, ConsumerSocket[IO]] = 
  for {
    subscriber <- topicSubscriberResource
    connected  <- subscriber.connect(Protocol.tcp("localhost", Port(31234)))
  } yield connected
```

Since `subscriber.connect` returns `Resource[F, ConsumerSocket[IO]]` the connection will be released automatically. 

## Configuration

Both `Subscriber[F]` and `ConsumerSocket[F]` have the same configuration methods:
```scala mdoc:silent
import io.fmq.domain.{ReceiveTimeout, Linger}
import io.fmq.socket.Subscriber

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
import io.fmq.ConsumerSocket
import fs2.Stream

def consumeSingleMessage(socket: ConsumerSocket[IO]): IO[String] = 
  socket.recvString

def consumeBatchMessage(socket: ConsumerSocket[IO]): IO[List[String]] = {
  def readBatch: Stream[IO, String] =
    for {
      s <- Stream.eval(socket.recvString)
      r <- Stream.eval(socket.hasReceiveMore).ifM(Stream.emit(s) ++ readBatch, Stream.emit(s))
    } yield r

  readBatch.compile.toList
}
```