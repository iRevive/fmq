package io.fmq.address

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class UriSpec extends AnyWordSpecLike with Matchers {

  "Uri" should {

    "support tcp protocol with host" in {
      val uri = Uri.Incomplete.TCP(Address.HostOnly(Host.Fixed("localhost")))

      uri.materialize shouldBe "tcp://localhost"
    }

    "support tcp protocol with host and port" in {
      val uri = Uri.Complete.TCP(Address.Full(Host.Wildcard, Port(1234)))

      uri.materialize shouldBe "tcp://*:1234"
    }

    "support inproc protocol with host" in {
      val uri = Uri.Complete.InProc(Address.HostOnly(Host.Fixed("localhost")))

      uri.materialize shouldBe "inproc://localhost"
    }

  }

}
