package com.repcheck.decomposition.text

import scala.util.control.NonFatal

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

/**
 * Extracts text from PDF bytes via PDFBox (in-memory, deterministic). For the reader layer's binary-PDF tail; the
 * caller supplies clean bytes (the DB's stored PDF bytes are corrupted).
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
