package io.fmq.frame

import cats.data.NonEmptyList
import weaver.SimpleIOSuite

object FrameSpec extends SimpleIOSuite {

  pureTest("Frame.Single has 1 part") {
    val frame = Frame.Single("Part 1")

    expect(frame.parts == NonEmptyList.one("Part 1"))
  }

  pureTest("Frame.Multipart has at least 2 parts") {
    val frame = Frame.Multipart("Part 1", "Part 2", "Part 3")

    expect(frame.parts == NonEmptyList.of("Part 1", "Part 2", "Part 3"))
  }

}
