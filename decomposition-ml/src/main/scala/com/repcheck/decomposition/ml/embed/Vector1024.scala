package com.repcheck.decomposition.ml.embed

/**
 * A validated 1024-dim embedding — the Ollama section/concept embedding dimensionality. Opaque so a raw `Vector[Float]`
 * cannot be passed where a 1024-vector is required; construct via [[Vector1024.of]] (returns `Left` on a wrong
 * dimension). The smart constructor is the only way to make one, so the dimension invariant holds everywhere
 * downstream.
 */
opaque type Vector1024 = Vector[Float]

object Vector1024 {

  val Dim: Int = 1024

  def of(values: Vector[Float]): Either[String, Vector1024] =
    if (values.sizeIs == Dim) Right(values)
    else Left(s"expected $Dim dimensions, got ${values.size.toString}")

  extension (v: Vector1024) {
    def values: Vector[Float]     = v
    def toDoubles: Vector[Double] = v.map(_.toDouble)
  }

}
