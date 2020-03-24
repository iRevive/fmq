package io.fmq.socket

import io.fmq.address.Uri

trait ConnectedSocket {
  def uri: Uri.Complete
}
