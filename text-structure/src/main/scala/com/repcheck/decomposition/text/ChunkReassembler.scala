package com.repcheck.decomposition.text

/**
 * Reassembles a bill's text from ordered `raw_bill_text` chunks. Chunks are verbatim slices, so concatenation restores
 * the original; overlap-aware so it's correct whether or not the chunker configures overlap (`maxOverlap = 0` = plain
 * concatenation). Chunks must be sorted by `chunk_index`.
 */
object ChunkReassembler {

  def reassemble(orderedChunks: List[String], maxOverlap: Int): String =
    orderedChunks match {
      case Nil          => ""
      case head :: tail => tail.foldLeft(head)((acc, next) => appendWithoutOverlap(acc, next, maxOverlap))
    }

  private def appendWithoutOverlap(acc: String, next: String, maxOverlap: Int): String = {
    val maxK = math.min(maxOverlap, math.min(acc.length, next.length))
    acc + next.substring(largestBoundaryOverlap(acc, next, maxK))
  }

  /** Largest k in [0, maxK] where acc's last k chars equal next's first k chars. */
  private def largestBoundaryOverlap(acc: String, next: String, maxK: Int): Int =
    (maxK to 1 by -1).find(k => acc.regionMatches(acc.length - k, next, 0, k)).getOrElse(0)

}
