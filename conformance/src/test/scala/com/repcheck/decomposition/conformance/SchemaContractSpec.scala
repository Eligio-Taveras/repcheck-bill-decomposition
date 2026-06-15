package com.repcheck.decomposition.conformance

import java.sql.{Connection, DriverManager, SQLException}

import com.repcheck.utils.tags.E2ETest

/**
 * Live cross-repo schema-parity self-test (DC-3). Tagged [[E2ETest]] so it is excluded from `sbt test` / CI and run on
 * demand against the local AlloyDB Omni:
 *
 * sbt "conformance/testOnly -- -n com.repcheck.tags.E2ETest"
 *
 * Connects via `$CONFORMANCE_JDBC_URL` (default jdbc:postgresql://localhost:5432/repcheck) and CANCELS (not fails) when
 * the DB is unreachable. The two [[TableRef]]s below are the read contract the decomposition pipeline depends on the
 * bills pipeline to keep stable (§13: it reassembles `raw_bill_text` chunks; reads `bill_text_versions` for stage).
 */
class SchemaContractSpec extends ConformanceContract {

  private val url  = sys.env.getOrElse("CONFORMANCE_JDBC_URL", "jdbc:postgresql://localhost:5432/repcheck")
  private val user = sys.env.getOrElse("CONFORMANCE_JDBC_USER", "repcheck")
  private val pass = sys.env.getOrElse("CONFORMANCE_JDBC_PASSWORD", "repcheck")

  private def withConn[A](f: Connection => A): A = {
    val conn =
      try DriverManager.getConnection(url, user, pass)
      catch {
        case e: SQLException => cancel(s"no AlloyDB at $url — start the local stack (${e.getMessage})")
      }
    try f(conn)
    finally conn.close()
  }

  // The columns ChunkReassembler + version selection actually consume. version_id is schema-nullable; the contract only
  // requires the column EXIST as bigint (row-level presence is the reader's concern, not the schema's).
  private val rawBillText = TableRef(
    "raw_bill_text",
    List(
      ColumnContract("bill_id", "bigint", nullable = false),
      ColumnContract("version_id", "bigint", nullable = true),
      ColumnContract("chunk_index", "integer", nullable = false),
      ColumnContract("content", "text", nullable = false),
    ),
  )

  private val billTextVersions = TableRef(
    "bill_text_versions",
    List(
      ColumnContract("id", "bigint", nullable = false),
      ColumnContract("format_type", "USER-DEFINED", nullable = true),
    ),
  )

  "the live raw_bill_text schema" should "satisfy the decomposition read contract" taggedAs E2ETest in {
    withConn(c => SchemaContract.verify(c, rawBillText) shouldBe empty)
  }

  "the live bill_text_versions schema" should "satisfy the decomposition read contract" taggedAs E2ETest in {
    withConn(c => SchemaContract.verify(c, billTextVersions) shouldBe empty)
  }

  "SchemaContract" should "report a violation for a missing column" taggedAs E2ETest in {
    val bogus =
      rawBillText.copy(columns = ColumnContract("does_not_exist", "text", nullable = true) :: rawBillText.columns)
    withConn(c => SchemaContract.verify(c, bogus).exists(_.contains("does_not_exist")) shouldBe true)
  }

  it should "report a violation for a missing table" taggedAs E2ETest in {
    val missing = TableRef("no_such_table", List(ColumnContract("x", "text", nullable = true)))
    withConn(c => SchemaContract.verify(c, missing) should not be empty)
  }

}
