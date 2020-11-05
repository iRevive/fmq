---
id: publisher
title: Publisher
---

The publisher socket can only publish messages into the queue.

## Allocation

The publisher can be created within the `Context`.     

```scala mdoc:silent
import cats.effect.{Resource, IO}
import io.fmq.Context
import io.fmq.socket.pubsub.Publisher

import scala.concurrent.ExecutionContext

implicit val contextShift[IO] = IO.contextShift(ExecutionContext.global)

val publisherResource: Resource[IO, Publisher[IO]] =
  for {
    blocker   <- Blocker[IO]
    context   <- Context.create[IO](1, blocker)
    publisher <- Resource.liftF(context.createPublisher)
  } yield publisher
```

The `Publisher[F]` is a valid instance of the socket but it's not connect to the network yet. 
Publisher can either connect to the specific port or allocate a random port.

```scala mdoc:silent
import io.fmq.socket.pubsub.Publisher
import io.fmq.syntax.literals._

val specificPort: Resource[IO, Publisher.Socket[IO]] = 
  for {
    publisher <- publisherResource
    connected <- publisher.bind(tcp"://localhost:31234")
  } yield connected

val randomPort: Resource[IO, Publisher.Socket[IO]] = 
  for {
    publisher <- publisherResource
    connected <- publisher.bindToRandomPort(tcp_i"://localhost")
  } yield connected
```

Since `publisher.bind` and `publisher.bindToRandomPort` return `Resource[F, ProducerSocket[IO]]` the connection will be released automatically. 

## Configuration

The settings can be changed until the socket is connected:  

```scala mdoc:silent
import io.fmq.options.{SendTimeout, Linger}
import io.fmq.socket.pubsub.Publisher

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
import io.fmq.frame.Frame

def sendSingleMessage(publisher: Publisher.Socket[IO]): IO[Unit] = 
  publisher.send("my-message")

def sendMultipartMessage(publisher: Publisher.Socket[IO]): IO[Unit] = 
  publisher.sendMultipart(Frame.Multipart("filter", "my-message")) 

def sendMultipartManually(publisher: Publisher.Socket[IO]): IO[Unit] = 
  publisher.sendMore("filter") >> publisher.send("my-message") 
```