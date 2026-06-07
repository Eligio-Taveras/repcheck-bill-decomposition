package com.repcheck.decomposition.text

/** One stored `raw_bill_text` row: its `chunk_index` and the verbatim text slice. */
final case class Chunk(index: Int, content: String)

/**
 * Reassembles a bill's text from its `raw_bill_text` chunks. The ingestion `TextChunker` emits contiguous,
 * non-overlapping verbatim slices, so concatenating them in `index` order restores the original exactly.
 *
 * Hard contract: the chunk set must be complete — indices exactly `0..n-1`, each present once. Reassembly fails loudly
 * (`Left`) on a gap, duplicate, or out-of-range index (a dropped or duplicated chunk row) rather than silently emitting
 * a truncated bill. There is no content-overlap handling: with contiguous indices and verbatim slices, concatenation is
 * exact, so non-overlap is the producer's invariant, not something to detect here.
 */
object ChunkReassembler {

  def reassemble(chunks: List[Chunk]): Either[ParseFailure, String] =
    validateIndices(chunks).map(_ => chunks.sortBy(_.index).map(_.content).mkString)

  private def validateIndices(chunks: List[Chunk]): Either[ParseFailure, Unit] = {
    val actual   = chunks.map(_.index).sorted
    val expected = chunks.indices.toList
    if (actual == expected) {
      Right(())
    } else {
      Left(
        ParseFailure(
          s"ChunkReassembler: expected contiguous chunk indices ${expected.mkString("[", ",", "]")}, got ${actual
              .mkString("[", ",", "]")}",
          None,
        )
      )
    }
  }

}
