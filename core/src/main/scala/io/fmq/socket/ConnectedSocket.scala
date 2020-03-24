package io.fmq.socket

import io.fmq.address.{Address, Complete, Protocol, Uri}

trait ConnectedSocket[P <: Protocol, A <: Address] {
  def uri: Uri[P, A]

  protected def complete: Complete[P, A]
}
