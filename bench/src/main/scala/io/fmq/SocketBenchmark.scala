package io.fmq

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import cats.effect.{Blocker, ContextShift, Fiber, IO, Resource}
import cats.syntax.flatMap._
import io.fmq.SocketBenchmark.MessagesCounter
import io.fmq.address.{Address, Host, Uri}
import org.openjdk.jmh.annotations._
import zmq.ZMQ

import scala.concurrent.ExecutionContext

//jmh:run io.fmq.SocketBenchmark
@BenchmarkMode(Array(Mode.Throughput))
@State(Scope.Benchmark)
@Fork(1)
@Threads(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.Var"))
class SocketBenchmark {

  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  @Param(Array("128", "256", "512", "1024"))
  var messageSize: Int = _

  private val recording = new AtomicBoolean

  private var publisher: Fiber[IO, Nothing] = _
  private var consumer: Fiber[IO, Nothing]  = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    val uri = Uri.Incomplete.TCP(Address.HostOnly(Host.Fixed("localhost")))

    val ((pull, push), _) =
      (for {
        blocker  <- Blocker[IO]
        context  <- Context.create[IO](1, blocker)
        pull     <- Resource.liftF(context.createPull)
        push     <- Resource.liftF(context.createPush)
        consumer <- pull.bindToRandomPort(uri)
        producer <- push.connect(consumer.uri)
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

object SocketBenchmark {

  @AuxCounters
  @State(Scope.Thread)
  class MessagesCounter {

    @Setup(Level.Iteration)
    def clean(): Unit = messagesCounter.set(0)

    def messagesPerSecond: Long = messagesCounter.get
  }

  private val messagesCounter = new AtomicLong

}
