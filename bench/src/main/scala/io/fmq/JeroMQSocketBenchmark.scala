package io.fmq

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import cats.effect.unsafe.IORuntime
import cats.effect.{Fiber, IO}
import io.fmq.JeroMQSocketBenchmark.MessagesCounter
import org.openjdk.jmh.annotations._
import org.zeromq.{SocketType, ZContext}
import zmq.ZMQ

//jmh:run io.fmq.JeroMQSocketBenchmark
@BenchmarkMode(Array(Mode.Throughput))
@State(Scope.Benchmark)
@Fork(1)
@Threads(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@SuppressWarnings(Array("org.wartremover.warts.All"))
class JeroMQSocketBenchmark {

  private implicit val runtime: IORuntime = IORuntime.global

  @Param(Array("128", "256", "512", "1024"))
  var messageSize: Int = _

  private val recording = new AtomicBoolean

  private var publisher: Fiber[IO, Throwable, Unit] = _
  private var consumer: Fiber[IO, Throwable, Unit]  = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    val ctx  = new ZContext()
    val addr = s"tcp://localhost"

    val pull = ctx.createSocket(SocketType.PULL)
    val port = pull.bindToRandomPort(addr)

    val push = ctx.createSocket(SocketType.PUSH)
    push.connect(addr + ":" + port)

    // Wait for connection
    Thread.sleep(500)

    val msg = ZMQ.msgInitWithSize(messageSize)

    val cleanup = IO.delay {
      push.close()
      pull.close()
    }

    publisher = IO
      .interruptible(false) {
        while (true) {
          push.send(msg.data())
        }
      }
      .start
      .unsafeRunSync()

    consumer = IO
      .interruptible(false) {
        while (true) {
          pull.recv()

          if (recording.get) {
            val _ = JeroMQSocketBenchmark.messagesCounter.addAndGet(1L)
          }
        }
      }
      .guarantee(cleanup)
      .start
      .unsafeRunSync()
  }

  @TearDown(Level.Iteration)
  def teardown(): Unit = {
    consumer.cancel.unsafeRunSync()
    publisher.cancel.unsafeRunSync()
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

object JeroMQSocketBenchmark {

  @AuxCounters
  @State(Scope.Thread)
  class MessagesCounter {

    @Setup(Level.Iteration)
    def clean(): Unit = messagesCounter.set(0)

    def messagesPerSecond: Long = messagesCounter.get
  }

  private val messagesCounter = new AtomicLong

}
