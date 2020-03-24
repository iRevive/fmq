---
id: consuming-strategy
title: Consuming strategy
---

You can use ƒMQ with any effect that has an instance of `cats.effect.Sync`: `cats.effect.IO`, `EitherT[IO, Error, *]` and so on.

## The problem
The `socket.receive` method blocks the thread until a new message is available.  
The `cats.effect.Blocker` allows to evaluate blocking operations on a separate execution context: `blocker.blockOn(socket.receive)`.  
So far so good, but if the expected throughput is high (e.g. 50k per second), you can face a performance degradation due to context switches.

There are several ways to solve the problem:


### 1) Call `socket.receive` without `Blocker`  
`fs2.Stream.repeatEval(socket.receive).map(msg => handleMessage(msg)`

The most straightforward solution. Since the message rate is high, the `socket.receive` operation returns the message almost immediately without blocking.

### 2) Evaluate the program entirely on the blocking context
`blocker.blockOn(fs2.Stream.repeatEval(socket.receive).map(msg => handleMessage(msg).compile.drain)`

The great disadvantage of this solution is evaluation of the lightweight operations on a blocking context. 

### 3) Separate consuming operation from the processing
`fs2.Stream.repeatEval(socket.receive)` can be evaluated on a blocking context in the background. 

```scala mdoc
import cats.effect.syntax.concurrent._
import cats.effect.{Blocker, Concurrent, ContextShift}
import fs2.Stream
import fs2.concurrent.Queue
import io.fmq.socket.ConsumerSocket

def consume[F[_]: Concurrent: ContextShift](blocker: Blocker, socket: ConsumerSocket[F]): Stream[F, String] = {
  def process(queue: Queue[F, String]) =
    blocker.blockOn(Stream.repeatEval(socket.receive[String]).through(queue.enqueue).compile.drain)

  for {
    queue  <- Stream.eval(Queue.unbounded[F, String])
    _      <- Stream.resource(process(queue).background)
    result <- queue.dequeue
  } yield result
}
```

The consuming process is being executed in the background on a dedicated thread, whilst further processing will be done in the general context.


