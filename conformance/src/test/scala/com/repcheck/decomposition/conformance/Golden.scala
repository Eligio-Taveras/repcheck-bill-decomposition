package com.repcheck.decomposition.conformance

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import scala.io.{Codec, Source}

import io.circe.{Json, Printer}

/**
 * Golden-output comparison (§10c#2). [[diff]] compares `actual` (canonical pretty-JSON, keys sorted) against the
 * committed `golden/<name>.json` on the classpath, returning a human-readable diff on mismatch or `None` on match.
 * Running with `-Dupdate.goldens=true` (forked tests, so cwd is the module dir) re-writes the golden under the running
 * module's `src/test/resources/golden/` instead of comparing — re-baselined diffs are reviewed in the PR. EXACT
 * compare: goldens hold STRUCTURAL artifacts (section boundaries, group memberships, node-id sets); nondeterministic
 * vectors/scores are compared via [[Tolerance]] and kept out of goldens.
 */
object Golden {

  private val canonical: Printer = Printer.spaces2.copy(sortKeys = true)

  private def updateMode: Boolean = sys.props.get("update.goldens").contains("true")

  def diff(name: String, actual: Json): Option[String] = {
    val rendered = canonical.print(actual)
    if (updateMode) {
      write(name, rendered)
      None
    } else {
      readGolden(name) match {
        case None =>
          Some(s"golden '$name' is missing — run tests with -Dupdate.goldens=true to create it")
        case Some(expected) if expected == rendered =>
          None
        case Some(expected) =>
          Some(
            s"golden '$name' mismatch (run -Dupdate.goldens=true to re-baseline)\n--- expected\n$expected\n--- actual\n$rendered"
          )
      }
    }
  }

  private def readGolden(name: String): Option[String] =
    Option(getClass.getClassLoader.getResourceAsStream(s"golden/$name.json")).map { is =>
      try Source.fromInputStream(is)(using Codec.UTF8).mkString.replace("\r\n", "\n")
      finally is.close()
    }

  private def write(name: String, rendered: String): Unit = {
    val dir = Paths.get("src", "test", "resources", "golden")
    val _   = Files.createDirectories(dir)
    val _   = Files.write(dir.resolve(s"$name.json"), rendered.getBytes(StandardCharsets.UTF_8))
  }

}
