package io.fmq.syntax

import io.fmq.address.{LiteralSyntaxMacros, Uri}

@SuppressWarnings(Array("org.wartremover.warts.ImplicitConversion"))
trait LiteralsSyntax {

  implicit final def fmqLiteralsSyntax(sc: StringContext): LiteralsOps =
    new LiteralsOps(sc)

}

class LiteralsOps(private val sc: StringContext) extends AnyVal {
  def tcp(args: Any*): Uri.Complete.TCP = macro LiteralSyntaxMacros.tcpComplete
  def inproc(args: Any*): Uri.Complete.InProc = macro LiteralSyntaxMacros.inprocComplete
  def tcp_i(args: Any*): Uri.Incomplete.TCP = macro LiteralSyntaxMacros.tcpIncomplete
}
