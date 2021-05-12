package io.fmq.proxy

import java.util.concurrent.Executors

import cats.effect.{IO, Resource}
import cats.syntax.flatMap._
import io.fmq.ContextSuite
import io.fmq.address.Uri
import io.fmq.frame.Frame
import io.fmq.options.Identity
import io.fmq.socket.pipeline.{Pull, Push}
import io.fmq.socket.pubsub.{Publisher, Subscriber}
import io.fmq.socket.reqrep.{Dealer, Reply, Request, Router}
import io.fmq.syntax.literals._
import weaver.Expectations

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * Tests are using IO.sleep(200.millis) to fix 'slow-joiner' problem.
  * More details: http://zguide.zeromq.org/page:all#Missing-Message-Problem-Solver
  */
object ProxySuite extends ContextSuite {

  private val singleThreadContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  test("proxy messages in bidirectional way") { ctx =>
    val frontendUri = inproc"://frontend"
    val backendUri  = inproc"://backend"

    def createProxySockets: Resource[IO, (Router.Socket[IO], Dealer.Socket[IO])] =
      for {
        router <- Resource.suspend(ctx.createRouter.map(_.bind(frontendUri)))
        dealer <- Resource.suspend(ctx.createDealer.map(_.bind(backendUri)))
      } yield (router, dealer)

    def createReqRepSockets: Resource[IO, (Request.Socket[IO], Reply.Socket[IO])] =
      for {
        request <- Resource.suspend(ctx.createRequest.map(_.connect(frontendUri)))
        reply   <- Resource.suspend(ctx.createReply.map(_.connect(backendUri)))
      } yield (request, reply)

    def program(client: Request.Socket[IO], server: Reply.Socket[IO]): IO[Expectations] =
      for {
        _   <- IO.sleep(500.millis)
        _   <- client.send("hello")
        req <- server.receive[String]
        _   <- server.send("reply")
        rep <- client.receive[String]
      } yield expect(req == "hello") and expect(rep == "reply")

    (for {
      (front, back)    <- createProxySockets
      proxy            <- ctx.proxy.bidirectional(front, back)
      _                <- proxy.start(singleThreadContext)
      (client, server) <- createReqRepSockets
    } yield (client, server)).use((program _).tupled)
  }

  test("proxy message in unidirectional way") { ctx =>
    val frontendUri = inproc"://frontend1"
    val backendUri  = inproc"://backend1"
    val controlUri  = inproc"://control1"

    def program(publisher: Publisher.Socket[IO], subscriber: Subscriber.Socket[IO], pull: Pull.Socket[IO]): IO[Expectations] =
      for {
        _       <- IO.sleep(500.millis)
        _       <- publisher.send("hello")
        msg     <- subscriber.receive[String]
        control <- pull.receive[String]
      } yield expect(msg == "hello") and expect(control == "hello")

    val topic = Subscriber.Topic.All

    (for {
      publisherProxy  <- Resource.suspend(ctx.createPublisher.map(_.bind(frontendUri)))
      publisher       <- Resource.suspend(ctx.createPublisher.map(_.bind(backendUri)))
      subscriberProxy <- Resource.suspend(ctx.createSubscriber(topic).map(_.connect(backendUri)))
      subscriber      <- Resource.suspend(ctx.createSubscriber(topic).map(_.connect(frontendUri)))
      pull            <- Resource.suspend(ctx.createPull.map(_.bind(controlUri)))
      push            <- Resource.suspend(ctx.createPush.map(_.connect(controlUri)))
      control         <- Resource.pure[IO, Control[IO]](Control.push(push))
      proxy           <- ctx.proxy.unidirectional(subscriberProxy, publisherProxy, Some(control))
      _               <- proxy.start(singleThreadContext)
    } yield (publisher, subscriber, pull)).use((program _).tupled)
  }

  test("control socket observed messages") { ctx =>
    val frontendUri = inproc"://frontend2"
    val backendUri  = inproc"://backend2"
    val controlUri  = inproc"://control2"

    val identity = Identity.utf8String("my-identity")

    def createProxySockets: Resource[IO, (Router.Socket[IO], Dealer.Socket[IO])] =
      for {
        router <- Resource.suspend(ctx.createRouter.map(_.bind(frontendUri)))
        dealer <- Resource.suspend(ctx.createDealer.map(_.bind(backendUri)))
      } yield (router, dealer)

    def createControlSockets: Resource[IO, (Pull.Socket[IO], Push.Socket[IO])] =
      for {
        pull <- Resource.suspend(ctx.createPull.map(_.bind(controlUri)))
        push <- Resource.suspend(ctx.createPush.map(_.connect(controlUri)))
      } yield (pull, push)

    def createReqRepSockets: Resource[IO, (Request.Socket[IO], Reply.Socket[IO])] =
      for {
        request <- Resource.suspend(ctx.createRequest.flatTap(_.setIdentity(identity)).map(_.connect(frontendUri)))
        reply   <- Resource.suspend(ctx.createReply.map(_.connect(backendUri)))
      } yield (request, reply)

    def program(client: Request.Socket[IO], server: Reply.Socket[IO], pull: Pull.Socket[IO]): IO[Expectations] =
      for {
        _          <- IO.sleep(500.millis)
        _          <- client.send("hello")
        req        <- server.receive[String]
        _          <- server.send("reply")
        rep        <- client.receive[String]
        controlReq <- pull.receiveFrame[String]
        controlRep <- pull.receiveFrame[String]
      } yield expect(req == "hello") and
        expect(rep == "reply") and
        expect(controlReq == Frame.Multipart("my-identity", "", "hello")) and // 1) identity; 2) pull terminator; 3) message
        expect(controlRep == Frame.Multipart("my-identity", "", "reply"))

    (for {
      (front, back)    <- createProxySockets
      (pull, push)     <- createControlSockets
      control          <- Resource.pure[IO, Control[IO]](Control.push(push))
      proxy            <- ctx.proxy.bidirectional(front, back, Some(control), Some(control))
      _                <- proxy.start(singleThreadContext)
      (client, server) <- createReqRepSockets
    } yield (client, server, pull)).use((program _).tupled)
  }

  test("separate control sockets observed messages") { ctx =>
    val frontendUri   = inproc"://frontend3"
    val backendUri    = inproc"://backend3"
    val controlInUri  = inproc"://controlIn3"
    val controlOutUri = inproc"://controlOut3"

    val identity = Identity.utf8String("my-identity")

    def createProxySockets: Resource[IO, (Router.Socket[IO], Dealer.Socket[IO])] =
      for {
        router <- Resource.suspend(ctx.createRouter.map(_.bind(frontendUri)))
        dealer <- Resource.suspend(ctx.createDealer.map(_.bind(backendUri)))
      } yield (router, dealer)

    def createControlSockets(uri: Uri.Complete.InProc): Resource[IO, (Pull.Socket[IO], Push.Socket[IO])] =
      for {
        pull <- Resource.suspend(ctx.createPull.map(_.bind(uri)))
        push <- Resource.suspend(ctx.createPush.map(_.connect(uri)))
      } yield (pull, push)

    def createReqRepSockets: Resource[IO, (Request.Socket[IO], Reply.Socket[IO])] =
      for {
        request <- Resource.suspend(ctx.createRequest.flatTap(_.setIdentity(identity)).map(_.connect(frontendUri)))
        reply   <- Resource.suspend(ctx.createReply.map(_.connect(backendUri)))
      } yield (request, reply)

    def program(
        client: Request.Socket[IO],
        server: Reply.Socket[IO],
        pullIn: Pull.Socket[IO],
        pullOut: Pull.Socket[IO]
    ): IO[Expectations] =
      for {
        _          <- IO.sleep(500.millis)
        _          <- client.send("hello")
        req        <- server.receive[String]
        _          <- server.send("reply")
        rep        <- client.receive[String]
        controlReq <- pullIn.receiveFrame[String]
        controlRep <- pullOut.receiveFrame[String]
      } yield expect(req == "hello") and
        expect(rep == "reply") and
        expect(controlReq == Frame.Multipart("my-identity", "", "hello")) and // 1) identity; 2) pull terminator; 3) message)
        expect(controlRep == Frame.Multipart("my-identity", "", "reply"))

    (for {
      (front, back)      <- createProxySockets
      (pullIn, pushIn)   <- createControlSockets(controlInUri)
      (pullOut, pushOut) <- createControlSockets(controlOutUri)
      controlIn          <- Resource.pure[IO, Control[IO]](Control.push(pushIn))
      controlOut         <- Resource.pure[IO, Control[IO]](Control.push(pushOut))
      proxy              <- ctx.proxy.bidirectional(front, back, Some(controlIn), Some(controlOut))
      _                  <- proxy.start(singleThreadContext)
      (client, server)   <- createReqRepSockets
    } yield (client, server, pullIn, pullOut)).use((program _).tupled)
  }

  test("start new proxy after termination") { ctx =>
    val frontendUri = inproc"://frontend4"
    val backendUri  = inproc"://backend4"

    def createProxySockets: Resource[IO, (Router.Socket[IO], Dealer.Socket[IO])] =
      for {
        router <- Resource.suspend(ctx.createRouter.map(_.bind(frontendUri)))
        dealer <- Resource.suspend(ctx.createDealer.map(_.bind(backendUri)))
      } yield (router, dealer)

    def createReqRepSockets: Resource[IO, (Request.Socket[IO], Reply.Socket[IO])] =
      for {
        request <- Resource.suspend(ctx.createRequest.map(_.connect(frontendUri)))
        reply   <- Resource.suspend(ctx.createReply.map(_.connect(backendUri)))
      } yield (request, reply)

    def verifyProxy: IO[Expectations] =
      createReqRepSockets.use { case (client, server) =>
        for {
          _   <- IO.sleep(500.millis)
          _   <- client.send("hello")
          req <- server.receive[String]
          _   <- server.send("reply")
          rep <- client.receive[String]
        } yield expect(req == "hello") and expect(rep == "reply")
      }

    def program(proxy: Proxy.Configured[IO]): IO[Expectations] =
      proxy.start(singleThreadContext).use(_ => verifyProxy) >> proxy.start(singleThreadContext).use(_ => verifyProxy)

    (for {
      (front, back) <- createProxySockets
      proxy         <- ctx.proxy.bidirectional(front, back)
    } yield proxy).use(program)
  }

}
