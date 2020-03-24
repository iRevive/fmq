---
id: publisher
title: Publisher
---

The publisher socket can only publish messages into the queue.

## Allocation

The publisher can be created within the `Context`.     

```scala mdoc:silent
import cats.effect.{Blocker, ContextShift, Resource, IO}
import io.fmq.Context
import io.fmq.socket.pubsub.Publisher

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
import io.fmq.address.{Address, Host, Port, Uri}
import io.fmq.socket.ProducerSocket
import io.fmq.socket.pubsub.Publisher

val specificPort: Resource[IO, ProducerSocket.TCP[IO]] = 
  for {
    publisher <- publisherResource
    connected <- publisher.bind(Uri.tcp(Address.Full(Host.Fixed("localhost"), Port(31234))))
  } yield connected

val randomPort: Resource[IO, ProducerSocket.TCP[IO]] = 
  for {
    publisher <- publisherResource
    connected <- publisher.bindToRandomPort(Uri.tcp(Address.HostOnly(Host.Fixed("localhost"))))
  } yield connected
```

Since `publisher.bind` and `publisher.bindToRandomPort` return `Resource[F, ProducerSocket[IO]]` the connection will be released automatically. 

## Configuration

The settings can be changed until the socket is connected:  

```scala mdoc:silent
import io.fmq.options.{SendTimeout, Linger}
import io.fmq.socket.ProducerSocket
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
import io.fmq.socket.ProducerSocket

def sendSingleMessage(publisher: ProducerSocket.TCP[IO]): IO[Unit] = 
  publisher.send("my-message")

def sendMultipartMessage(publisher: ProducerSocket.TCP[IO]): IO[Unit] = 
  publisher.sendMultipart(Frame.Multipart("filter", "my-message")) 

def sendMultipartManually(publisher: ProducerSocket.TCP[IO]): IO[Unit] = 
  publisher.sendMore("filter") >> publisher.send("my-message") 
```