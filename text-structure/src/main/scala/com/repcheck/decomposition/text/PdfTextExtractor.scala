package com.repcheck.decomposition.text

import scala.util.control.NonFatal

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

/**
 * Extracts plain text from PDF bytes via Apache PDFBox. Deterministic, in-memory (no disk/network).
 *
 * Used for the small tail of PDF-format versions whose `raw_bill_text` content is raw PDF binary rather than
 * already-extracted text. NOTE: those bytes are corrupted in the DB (stored into a UTF-8 `TEXT` column → replacement
 * chars), so clean bytes must be supplied by the reader layer (re-fetched original) — this extractor takes the bytes;
 * sourcing them is the caller's job. The extracted text is then handed to [[GpoTextSectionParser]] like any
 * Formatted-Text content.
 */
object PdfTextExtractor {

  def extract(pdfBytes: Array[Byte]): Either[ParseFailure, String] =
    try {
      val document = Loader.loadPDF(pdfBytes)
      try
        Right(new PDFTextStripper().getText(document))
      finally
        document.close()
    } catch {
      case NonFatal(t) => Left(ParseFailure("PDF: text extraction failed", Some(t)))
    }

  /** True if the content looks like raw PDF bytes (rather than already-extracted text). */
  def looksLikePdfBinary(content: String): Boolean = content.stripLeading().startsWith("%PDF")
}
