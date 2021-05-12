package io.fmq.address

import weaver.SimpleIOSuite

object UriSuite extends SimpleIOSuite {

  pureTest("support tcp protocol with host") {
    val uri = Uri.Incomplete.TCP(Address.HostOnly("localhost"))

    expect(uri.materialize == "tcp://localhost")
  }

  pureTest("support tcp protocol with host and port") {
    val uri = Uri.Complete.TCP(Address.Full("127.0.0.1", 1234))

    expect(uri.materialize == "tcp://127.0.0.1:1234")
  }

  pureTest("support inproc protocol with host") {
    val uri = Uri.Complete.InProc(Address.HostOnly("localhost"))

    expect(uri.materialize == "inproc://localhost")
  }

}
