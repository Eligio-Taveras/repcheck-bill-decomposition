package com.repcheck.decomposition.text

import java.io.ByteArrayOutputStream

import org.apache.pdfbox.pdmodel.font.{PDType1Font, Standard14Fonts}
import org.apache.pdfbox.pdmodel.{PDDocument, PDPage, PDPageContentStream}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PdfTextExtractorSpec extends AnyFlatSpec with Matchers {

  /** Build a one-page PDF containing `text`, as bytes (round-trips through real PDFBox). */
  private def pdfBytes(text: String): Array[Byte] = {
    val document = new PDDocument()
    try {
      val page = new PDPage()
      document.addPage(page)
      val stream = new PDPageContentStream(document, page)
      try {
        stream.beginText()
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12)
        stream.newLineAtOffset(50, 700)
        stream.showText(text)
        stream.endText()
      } finally stream.close()
      val out = new ByteArrayOutputStream()
      document.save(out)
      out.toByteArray
    } finally document.close()
  }

  "PdfTextExtractor" should "extract text from real PDF bytes" in {
    PdfTextExtractor.extract(pdfBytes("SECTION 1. SHORT TITLE.")) match {
      case Right(text) => text should include("SECTION 1.")
      case Left(f)     => fail(s"expected Right, got $f")
    }
  }

  it should "return Left on non-PDF bytes rather than throw" in {
    PdfTextExtractor.extract("not a pdf at all".getBytes("UTF-8")) match {
      case Left(f)  => f.message should include("text extraction failed")
      case Right(r) => fail(s"expected Left, got '$r'")
    }
  }

  "looksLikePdfBinary" should "detect a raw PDF header but not extracted text" in {
    PdfTextExtractor.looksLikePdfBinary("%PDF-1.5 stream ...") shouldBe true
    PdfTextExtractor.looksLikePdfBinary("  %PDF-1.7") shouldBe true
    PdfTextExtractor.looksLikePdfBinary("SECTION 1. SHORT TITLE.") shouldBe false
  }

}
