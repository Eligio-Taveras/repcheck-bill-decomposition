package com.repcheck.decomposition.text

/** Flat, unique parse failure (no sealed hierarchy — project convention). Message form: `"<source>: <reason>"`. */
final case class ParseFailure(message: String, cause: Option[Throwable]) extends Exception(message)
