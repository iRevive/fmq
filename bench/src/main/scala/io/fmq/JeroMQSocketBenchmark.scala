package io.fmq

import java.util.Random
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import cats.effect.{ContextShift, Fiber, IO}
import io.fmq.JeroMQSocketBenchmark.MessagesCounter
import org.openjdk.jmh.annotations._
import zmq.{SocketBase, ZMQ}

import scala.concurrent.ExecutionContext

//jmh:run io.fmq.JeroMQSocketBenchmark
@BenchmarkMode(Array(Mode.Throughput))
@State(Scope.Benchmark)
@Fork(1)
@Threads(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@SuppressWarnings(Array("org.wartremover.warts.All"))
class JeroMQSocketBenchmark {

  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  @Param(Array("128", "256", "512", "1024", "1048576"))
  var messageSize: Int = _

  private val recording = new AtomicBoolean
  private var port      = 30513

  private var publisher: Fiber[IO, Unit] = _
  private var consumer: Fiber[IO, Unit]  = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    port += 1

    val ctx  = ZMQ.init(1)
    val addr = s"tcp://localhost:$port"

    val pull = ZMQ.socket(ctx, ZMQ.ZMQ_PULL)
    pull.bind(addr)

    val push = ZMQ.socket(ctx, ZMQ.ZMQ_PUSH)
    push.connect(addr)

    // Wait for connection
    Thread.sleep(500)

    val msg = ZMQ.msgInitWithSize(messageSize)

    val cleanup = IO.delay {
      ZMQ.close(pull)
      ZMQ.close(push)
    }

    publisher = IO
      .delay {
        while (true) {
          ZMQ.sendMsg(push, msg, 0)
        }
      }
      .start
      .unsafeRunSync()

    consumer = IO
      .delay {
        while (true) {
          ZMQ.recvMsg(pull, 0)

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

  @SuppressWarnings(Array("org.wartremover.warts.All"))
  def bindToRandomPort(socket: SocketBase, addr: String, min: Int, max: Int): Int = {
    var port = 0
    val rand = new Random

    for (_ <- 0 until 100) { // hardcoded to 100 tries. should this be parametrised
      port = rand.nextInt(max - min + 1) + min
      if (socket.bind(String.format("%s:%s", addr, port))) {
        return port
      }
    }

    sys.error("Could not bind socket to random port")
  }

}
