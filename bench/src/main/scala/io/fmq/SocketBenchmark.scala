package io.fmq

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import cats.effect.unsafe.IORuntime
import cats.effect.{Fiber, IO, Resource}
import io.fmq.SocketBenchmark.MessagesCounter
import io.fmq.syntax.literals._
import org.openjdk.jmh.annotations._
import zmq.ZMQ

//jmh:run io.fmq.SocketBenchmark
@BenchmarkMode(Array(Mode.Throughput))
@State(Scope.Benchmark)
@Fork(1)
@Threads(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.Var"))
class SocketBenchmark {

  private implicit val runtime: IORuntime = IORuntime.global

  @Param(Array("128", "256", "512", "1024"))
  var messageSize: Int = _

  private val recording = new AtomicBoolean

  private var publisher: Fiber[IO, Throwable, Nothing] = _
  private var consumer: Fiber[IO, Throwable, Nothing]  = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    val uri = tcp_i"://localhost"

    val ((pull, push), _) =
      (for {
        context  <- Context.create[IO](1)
        consumer <- Resource.suspend(context.createPull.map(_.bindToRandomPort(uri)))
        producer <- Resource.suspend(context.createPush.map(_.connect(consumer.uri)))
      } yield (consumer, producer)).allocated.unsafeRunSync()

    // Wait for connection
    Thread.sleep(200)

    val msg = ZMQ.msgInitWithSize(messageSize)

    def inc(): Unit = if (recording.get) {
      val _ = SocketBenchmark.messagesCounter.addAndGet(1L)
    }

    publisher = push.send(msg.data()).foreverM.start.unsafeRunSync()
    consumer = pull.receive[Array[Byte]].map(_ => inc()).foreverM.start.unsafeRunSync()
  }

  @TearDown(Level.Iteration)
  def teardown(): Unit = {
    consumer.cancel.unsafeRunAndForget()
    publisher.cancel.unsafeRunAndForget()
  }

  @Benchmark
  def messagesPerSecond(counter: MessagesCounter): Unit = {
    val _ = counter
    recording.set(true)
    // Send as many messages as we can in a second.
    Thread.sleep(1001)
    recording.set(false)
  }

}

@SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.Var"))
object SocketBenchmark {

  @AuxCounters
  @State(Scope.Thread)
  class MessagesCounter {

    @Setup(Level.Iteration)
    def clean(): Unit = {
      messagesCounter = new AtomicLong
    }

    def messagesPerSecond: Long = messagesCounter.get
  }

  private var messagesCounter = new AtomicLong

}
