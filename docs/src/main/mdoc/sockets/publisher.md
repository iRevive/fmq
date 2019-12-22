---
layout: docs
title:  "Publisher"
number: 2
---

# Publisher

The publisher socket can only publish messages into the queue.

## Allocation

The publisher can be created within the `Context`.     

```scala mdoc:silent
import cats.effect.{Blocker, ContextShift, Resource, IO}
import io.fmq.Context
import io.fmq.socket.Publisher

import scala.concurrent.ExecutionContext

implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

val publisherResource: Resource[IO, Publisher[IO]] =
  for {
    blocker   <- Blocker[IO]
    context   <- Context.create[IO](1, blocker)
    publisher <- context.createPublisher
  } yield publisher
```

The `Publisher[F]` is a valid instance of the socket but it's not connect to the network yet. 
Publisher can either connect to the specific port or allocate a random port.

```scala mdoc:silent
import io.fmq.domain.{Protocol, Port}
import io.fmq.socket.Publisher
import io.fmq.ProducerSocket

val specificPort: Resource[IO, ProducerSocket[IO]] = 
  for {
    publisher <- publisherResource
    connected <- publisher.bind(Protocol.tcp("localhost", Port(31234)))
  } yield connected

val randomPort: Resource[IO, ProducerSocket[IO]] = 
  for {
    publisher <- publisherResource
    connected <- publisher.bindToRandomPort(Protocol.tcp("localhost"))
  } yield connected
```

Since `publisher.bind` and `publisher.bindToRandomPort` return `Resource[F, ProducerSocket[IO]]` the connection will be released automatically. 

## Configuration

Both `Publisher[F]` and `ProducerSocket[F]` have the same configuration methods:

```scala mdoc:silent
import io.fmq.domain.{SendTimeout, Linger}
import io.fmq.socket.Publisher
import io.fmq.ProducerSocket

def configurePublisher(publisher: Publisher[IO]): IO[Unit] = 
  for {
    _ <- publisher.setSendTimeout(SendTimeout.Immediately)
    _ <- publisher.setLinger(Linger.Immediately)
  } yield ()
```

## Publish messages

Only connected publisher can send messages:

```scala mdoc:silent
import cats.syntax.flatMap._
import io.fmq.ProducerSocket

def sendSingleMessage(publisher: ProducerSocket[IO]): IO[Unit] = 
  publisher.sendString("my-message")

def sendBatchMessage(publisher: ProducerSocket[IO]): IO[Unit] = 
  publisher.sendStringMore("filter") >> publisher.sendString("my-message") 
```