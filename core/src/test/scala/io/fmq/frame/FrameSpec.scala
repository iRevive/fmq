package io.fmq.frame

import cats.data.NonEmptyList
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class FrameSpec extends AnyWordSpecLike with Matchers {

  "Frame.Single" should {

    "have 1 part" in {
      val frame = Frame.Single("Part 1")

      frame.parts shouldBe NonEmptyList.one("Part 1")
    }

  }

  "Frame.Multipart" should {

    "concat header and tail" in {
      val frame = Frame.Multipart("Part 1", "Part 2", "Part 3")

      frame.parts shouldBe NonEmptyList.of("Part 1", "Part 2", "Part 3")
    }

  }

}
