package com.repcheck.decomposition.conformance

import scala.io.Source

/**
 * The single conformance corpus (§10c#5) — bills reassembled from the local AlloyDB's `raw_bill_text` by
 * `scripts/build-corpus.sh` and committed under `conformance/src/main/resources/corpus`. One source of truth for parser
 * / cluster / golden / E2E tests; read from the committed fixture, never the DB. `format` is the raw Congress.gov
 * `format_type` label ("Formatted Text" / "Formatted XML" / "PDF") — consumers map it to their own type.
 */
object Corpus {

  final case class Bill(versionId: String, billType: String, format: String, content: String)

  /** All pinned bills, loaded from the committed manifest + per-bill text files. */
  lazy val bills: List[Bill] =
    readResource("corpus/manifest.tsv").linesIterator.drop(1).filter(_.nonEmpty).toList.map { line =>
      line.split("\t", -1).toList match {
        case versionId :: _ :: billType :: format :: _ =>
          Bill(versionId, billType, format, readResource(s"corpus/$versionId.txt"))
        case other =>
          sys.error(s"corpus manifest row malformed: $other")
      }
    }

  private def readResource(path: String): String = {
    val src = Source.fromResource(path)
    try src.mkString
    finally src.close()
  }

}
