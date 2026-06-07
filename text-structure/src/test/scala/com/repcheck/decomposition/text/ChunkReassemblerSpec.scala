package com.repcheck.decomposition.text

import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChunkReassemblerSpec extends AnyFlatSpec with Matchers with Inspectors {

  private def chunks(parts: String*): List[Chunk] =
    parts.toList.zipWithIndex.map { case (c, i) => Chunk(i, c) }

  "ChunkReassembler" should "return empty string for no chunks" in {
    ChunkReassembler.reassemble(Nil) shouldBe Right("")
  }

  it should "return the single chunk unchanged" in {
    ChunkReassembler.reassemble(chunks("the whole bill")) shouldBe Right("the whole bill")
  }

  it should "concatenate contiguous slices verbatim" in {
    val full = "SECTION 1. SHORT TITLE. This Act may be cited as the Test Act. SEC. 2. FINDINGS."
    ChunkReassembler.reassemble(full.grouped(20).toList.zipWithIndex.map { case (c, i) => Chunk(i, c) }) shouldBe
      Right(full)
  }

  it should "reorder by index before concatenating (input order does not matter)" in {
    ChunkReassembler.reassemble(List(Chunk(2, "c"), Chunk(0, "a"), Chunk(1, "b"))) shouldBe Right("abc")
  }

  it should "losslessly reconstruct an arbitrary document across many chunk sizes" in {
    val full = (1 to 500).map(i => s"word$i").mkString(" ")
    forEvery(List(1, 7, 16, 100, 999)) { size =>
      val parts = full.grouped(size).toList.zipWithIndex.map { case (c, i) => Chunk(i, c) }
      ChunkReassembler.reassemble(parts) shouldBe Right(full)
    }
  }

  it should "fail loudly on a missing index (dropped chunk row)" in {
    ChunkReassembler.reassemble(List(Chunk(0, "a"), Chunk(2, "c"))) match {
      case Left(f)  => f.message should include("contiguous chunk indices")
      case Right(r) => fail(s"expected Left, got $r")
    }
  }

  it should "fail loudly on a duplicate index" in {
    ChunkReassembler.reassemble(List(Chunk(0, "a"), Chunk(0, "a"))) match {
      case Left(f)  => f.message should include("got [0,0]")
      case Right(r) => fail(s"expected Left, got $r")
    }
  }

  it should "fail loudly when indices do not start at 0" in {
    ChunkReassembler.reassemble(List(Chunk(1, "a"), Chunk(2, "b"))) match {
      case Left(f)  => f.message should include("expected contiguous chunk indices [0,1]")
      case Right(r) => fail(s"expected Left, got $r")
    }
  }

}
