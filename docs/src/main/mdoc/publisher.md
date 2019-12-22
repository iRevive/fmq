---
layout: docs
title:  "Publisher"
number: 2
---

# Publisher

The publisher can be created within the `Context`.     

```scala mdoc:silent
import cats.effect.{Blocker, Resource, IO}
import io.fmq.Context
import io.fmq.socket.Publisher

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
import io.fmq.domain.Protocol

val specificPort: Resource[IO, Publisher.Connected[IO]] = 
  for {
    publisher <- publisherResource
    connected <- publisher.bind(Protocol.tcp("localhost", 31234))
  } yield connected

val randomPort: Resource[IO, Publisher.Connected[IO]] = 
  for {
    publisher <- publisherResource
    connected <- publisher.bindToRandomPort(Protocol.tcp("localhost"))
  } yield connected
```

Since `publisher.bind` and `publisher.bindToRandomPort` return `Resource[F, Publisher.Connected[IO]]` 
the connection will be released automatically. 

# Configuration

Both `Publisher[F]` and `Publisher.Connected[F]` have the same configuration methods:
```scala mdoc:silent
import io.fmq.domain.SendTimeout
import io.fmq.domain.Linger

def configurePublisher(publisher: Publisher[IO]): IO[Unit] = 
  for {
    _ <- publisher.setSendTimeout(SendTimeout.NoDelay)
    _ <- publisher.setLinger(Linger.Immediately)
  } yield ()
```
