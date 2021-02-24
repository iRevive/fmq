---
id: consuming-strategy
title: Consuming strategy
---

You can use ƒMQ with any effect that has an instance of `cats.effect.Sync`: `cats.effect.IO`, `EitherT[IO, Error, *]` and so on.

## The problem
The `socket.receive` method blocks the thread until a new message is available.  
The `cats.effect.Async` allows to evaluate blocking operations on a separate execution context: `socker.receive.evalOn(blocker)`.  
So far so good, but if the expected throughput is high (e.g. 50k per second), you can face a performance degradation due to context switches.

There are several ways to solve the problem:


### 1) Call `socket.receive` directly
`fs2.Stream.repeatEval(socket.receive).map(msg => handleMessage(msg)`

The most straightforward solution. Since the message rate is high, the `socket.receive` operation returns the message almost immediately without blocking.

### 2) Evaluate the program entirely on the blocking context
`fs2.Stream.repeatEval(socket.receive).map(msg => handleMessage(msg).compile.drain).evalOn(blocker)`

The great disadvantage of this approach is evaluation of the lightweight operations on a blocking context. 

### 3) Separate consuming operation from the processing
`fs2.Stream.repeatEval(socket.receive)` can be evaluated on a blocking context in the background. 

```scala mdoc
import cats.effect.syntax.async._
import cats.effect.Async
import cats.effect.std.Queue
import fs2.Stream
import io.fmq.socket.ConsumerSocket

import scala.concurrent.ExecutionContext

def consume[F[_]: Async](blocker: ExecutionContext, socket: ConsumerSocket[F]): Stream[F, String] = {
  def process(queue: Queue[F, String]) =
    Stream.repeatEval(socket.receive[String]).evalMap(queue.offer).compile.drain

  for {
    queue  <- Stream.eval(Queue.unbounded[F, String])
    _      <- Stream.resource(process(queue).backgroundOn(blocker))
    result <- Stream.repeatEval(queue.take)
  } yield result
}
```

The consuming process is being executed in the background on a dedicated thread, whilst further processing will be done in the general context.


