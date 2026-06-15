package com.repcheck.decomposition.conformance

import io.circe.Json

import net.reactivecore.cjs.{DocumentValidator, Loader}
import org.scalacheck.Arbitrary
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import repcheck.shared.models.llm.codec.StructuredCodec

/**
 * §10c#5b `StructuredCodec` law for a schema-bound (LLM-boundary) type: for the `canonicalExample` AND every
 * `sampleGen` sample, `decode(encode(a)) == a` (round-trip) AND `encode(a)` validates against `jsonSchema` — so a
 * type's schema, example, sample-generator, and codec can never silently diverge. Mirrors shared-models' own
 * `StructuredCodecLaws` (test-scoped, so not importable cross-repo; the F1 types stay tested there). Each decomposition
 * codec-bound type (a tool's `In`/`Out`, D4) ships a concrete subclass.
 */
abstract class StructuredCodecLaws[A](name: String)(using sc: StructuredCodec[A])
    extends ConformanceContract
    with ScalaCheckPropertyChecks {

  private val validator: DocumentValidator =
    Loader.empty.fromJson(sc.jsonSchema) match {
      case Right(v) => v
      case Left(e)  => fail(s"$name: jsonSchema is not a valid JSON Schema: $e")
    }

  private def schemaErrors(instance: Json): Seq[String] =
    validator.validate(instance).violations.map(_.toString)

  private given Arbitrary[A] = Arbitrary(sc.sampleGen)

  s"$name StructuredCodec" should "round-trip and schema-validate the canonical example" in {
    val _ = sc.decoder.decodeJson(sc.exampleJson) shouldBe Right(sc.canonicalExample)
    schemaErrors(sc.exampleJson) shouldBe empty
  }

  it should "round-trip and schema-validate every generated sample" in {
    forAll { (a: A) =>
      val _ = sc.decoder.decodeJson(sc.encoder(a)) shouldBe Right(a)
      schemaErrors(sc.encoder(a)) shouldBe empty
    }
  }

}
