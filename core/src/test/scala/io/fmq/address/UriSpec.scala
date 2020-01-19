package io.fmq.address

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class UriSpec extends AnyWordSpecLike with Matchers {

  "Uri" should {

    "support tcp protocol with host" in {
      val uri = Uri.tcp(Address.HostOnly(Host.Fixed("localhost")))

      uri.materialize shouldBe "tcp://localhost"
      uri.isComplete shouldBe false
    }

    "support tcp protocol with host and port" in {
      val uri = Uri.tcp(Address.Full(Host.Wildcard, Port(1234)))

      uri.materialize shouldBe "tcp://*:1234"
      uri.isComplete shouldBe true
    }

    "support inproc protocol with host" in {
      val uri = Uri.inproc(Address.HostOnly(Host.Fixed("localhost")))

      uri.materialize shouldBe "inproc://localhost"
      uri.isComplete shouldBe true
    }

  }

}
