package io.fmq.frame

import java.nio.charset.StandardCharsets

trait FrameDecoder[A] {
  def decode(bytes: Array[Byte]): A
}

object FrameDecoder {

  def apply[A](implicit instance: FrameDecoder[A]): FrameDecoder[A] = instance

  implicit val byteArrayDecoder: FrameDecoder[Array[Byte]] = identity

  implicit val utf8stringDecoder: FrameDecoder[String] = bytes => new String(bytes, StandardCharsets.UTF_8)

}
