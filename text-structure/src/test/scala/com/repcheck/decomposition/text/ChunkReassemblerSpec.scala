package com.repcheck.decomposition.text

import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChunkReassemblerSpec extends AnyFlatSpec with Matchers with Inspectors {

  "ChunkReassembler" should "return empty string for no chunks" in {
    ChunkReassembler.reassemble(Nil, maxOverlap = 0) shouldBe ""
  }

  it should "return the single chunk unchanged" in {
    ChunkReassembler.reassemble(List("the whole bill"), maxOverlap = 0) shouldBe "the whole bill"
  }

  it should "concatenate contiguous (non-overlapping) chunks verbatim" in {
    val full   = "SECTION 1. SHORT TITLE. This Act may be cited as the Test Act. SEC. 2. FINDINGS."
    val chunks = full.grouped(20).toList // exact-size slices, no overlap (mirrors TextChunker)
    ChunkReassembler.reassemble(chunks, maxOverlap = 0) shouldBe full
  }

  it should "drop a duplicated boundary when chunks overlap" in {
    // acc ends with "the act", next begins with "the act" — overlap of 7 chars.
    val chunks = List("This is the act", "the act applies to all")
    ChunkReassembler.reassemble(chunks, maxOverlap = 10) shouldBe "This is the act applies to all"
  }

  it should "not invent an overlap when none exists (plain concat even with a high maxOverlap)" in {
    ChunkReassembler.reassemble(List("abc", "def"), maxOverlap = 50) shouldBe "abcdef"
  }

  it should "losslessly reconstruct an arbitrary document across many chunk sizes" in {
    val full = (1 to 500).map(i => s"word$i").mkString(" ")
    forEvery(List(1, 7, 16, 100, 999)) { size =>
      ChunkReassembler.reassemble(full.grouped(size).toList, maxOverlap = 0) shouldBe full
    }
  }

}
