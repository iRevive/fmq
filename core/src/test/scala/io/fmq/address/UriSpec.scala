package io.fmq.address

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class UriSpec extends AnyWordSpecLike with Matchers {

  "Uri" should {

    "support tcp protocol with host" in {
      val uri = Uri.Incomplete.TCP(Address.HostOnly("localhost"))

      uri.materialize shouldBe "tcp://localhost"
    }

    "support tcp protocol with host and port" in {
      val uri = Uri.Complete.TCP(Address.Full("127.0.0.1", 1234))

      uri.materialize shouldBe "tcp://127.0.0.1:1234"
    }

    "support inproc protocol with host" in {
      val uri = Uri.Complete.InProc(Address.HostOnly("localhost"))

      uri.materialize shouldBe "inproc://localhost"
    }

  }

}
