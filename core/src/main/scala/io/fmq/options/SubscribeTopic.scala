package io.fmq.options

import java.nio.charset.StandardCharsets

sealed trait SubscribeTopic {
  def value: Array[Byte]
}

object SubscribeTopic {

  @SuppressWarnings(Array("org.wartremover.warts.ArrayEquals"))
  final case class Bytes(value: Array[Byte]) extends SubscribeTopic

  final case object All extends SubscribeTopic {
    override val value: Array[Byte] = Array.empty
  }

  def utf8String(value: String): SubscribeTopic.Bytes = Bytes(value.getBytes(StandardCharsets.UTF_8))

}
