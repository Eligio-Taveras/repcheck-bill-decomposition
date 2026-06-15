package com.repcheck.decomposition.conformance

/**
 * Reusable determinism / idempotency invariants (§10c#6) for property tests: a parse is byte-identical, a derived key
 * (e.g. `groupId` from sorted member ids) is invariant to input order, a forced re-run UPSERTs to identical rows. Drive
 * these with a ScalaCheck `forAll` over the relevant inputs.
 */
object DeterminismLaws {

  /** `f` returns the same output for the same input. */
  def isDeterministic[A, B](f: A => B)(a: A): Boolean = f(a) == f(a)

  /** Applying `f` again to its own output changes nothing. */
  def isIdempotent[A](f: A => A)(a: A): Boolean = {
    val once = f(a)
    f(once) == once
  }

  /** A value derived from a set of keys is invariant to input order (e.g. `groupId` from sorted member ids). */
  def orderInvariant[K: Ordering, V](derive: List[K] => V)(a: List[K], b: List[K]): Boolean =
    if (a.sorted == b.sorted) derive(a) == derive(b) else true

}
