package com.repcheck.decomposition.text

/**
 * Reassembles a bill's full document text from its ordered `raw_bill_text` chunks.
 *
 * Chunks are verbatim character slices the ingestion pipeline cut at the Ollama per-request character limit (no
 * summarization, no transformation), so concatenating them in `chunk_index` order restores the original exactly. PURE +
 * deterministic.
 *
 * The upstream `TextChunker` slices contiguously (no overlap), so this is normally plain concatenation. It is
 * nonetheless overlap-aware: if adjacent chunks share a boundary region, the duplicated head of the next chunk is
 * dropped — so reassembly is correct whether or not the pipeline ever configures chunk overlap. `maxOverlap = 0` forces
 * strict concatenation.
 */
object ChunkReassembler {

  /**
   * @param orderedChunks
   *   chunk contents already sorted by `chunk_index` ascending.
   * @param maxOverlap
   *   upper bound (chars) on the boundary overlap to look for between adjacent chunks; 0 means assume contiguous
   *   (concatenate verbatim).
   */
  def reassemble(orderedChunks: List[String], maxOverlap: Int): String =
    orderedChunks match {
      case Nil          => ""
      case head :: tail => tail.foldLeft(head)((acc, next) => stitch(acc, next, maxOverlap))
    }

  /**
   * Append `next` to `acc`, dropping the leading part of `next` that duplicates the trailing part of `acc`. With no
   * shared boundary (or `maxOverlap == 0`) this is plain concatenation.
   */
  private def stitch(acc: String, next: String, maxOverlap: Int): String = {
    val maxK = math.min(maxOverlap, math.min(acc.length, next.length))
    acc + next.substring(largestBoundaryOverlap(acc, next, maxK))
  }

  /** Largest k in [0, maxK] where acc's last k chars equal next's first k chars. */
  private def largestBoundaryOverlap(acc: String, next: String, maxK: Int): Int =
    (maxK to 1 by -1).find(k => acc.regionMatches(acc.length - k, next, 0, k)).getOrElse(0)

}
