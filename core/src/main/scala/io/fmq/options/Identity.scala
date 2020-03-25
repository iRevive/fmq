package io.fmq.options

import java.nio.charset.StandardCharsets

@SuppressWarnings(Array("org.wartremover.warts.ArrayEquals"))
final case class Identity(value: Array[Byte])

object Identity {
  def utf8String(string: String): Identity = Identity(string.getBytes(StandardCharsets.UTF_8))
}
