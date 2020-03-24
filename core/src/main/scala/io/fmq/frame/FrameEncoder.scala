package io.fmq.frame

import java.nio.charset.StandardCharsets

trait FrameEncoder[A] {
  def encode(value: A): Array[Byte]
}

object FrameEncoder {

  def apply[A](implicit instance: FrameEncoder[A]): FrameEncoder[A] = instance

  implicit val byteArrayEncoder: FrameEncoder[Array[Byte]] = identity

  implicit val utf8stringEncoder: FrameEncoder[String] = s => s.getBytes(StandardCharsets.UTF_8)

}
