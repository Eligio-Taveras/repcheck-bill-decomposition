package com.repcheck.decomposition.conformance

import java.sql.Connection

/**
 * One column the read-side requires: its name, the `information_schema.columns.data_type`, and whether the read
 * tolerates a NULLABLE column. `nullable = false` means the contract REQUIRES `NOT NULL`; `nullable = true` accepts
 * either. Type is matched against Postgres `data_type` (e.g. "bigint", "integer", "text", "USER-DEFINED" for enums /
 * pgvector).
 */
final case class ColumnContract(name: String, dataType: String, nullable: Boolean)

/**
 * The columns a read-side phase consumes from one table. Kept string-keyed on purpose (DC-3): wiring these names to the
 * shared `Tables.*` constants is deferred to the first phase that writes SQL.
 */
final case class TableRef(name: String, columns: List[ColumnContract])

/**
 * Cross-repo schema-parity harness (§10c, DC-3). A read-side phase declares a [[TableRef]] for each table it reads; the
 * upstream pipeline OWNS the schema. [[verify]] introspects the LIVE table on a JDBC connection and reports every way
 * the live schema fails the read contract — missing column, wrong `data_type`, or NULLABLE where NOT NULL is required.
 * Empty result == contract satisfied. Live columns the contract does not mention are ignored (the writer may add
 * columns freely; "live is a superset"). DC-4 runs this against the local AlloyDB as a parity gate.
 */
object SchemaContract {

  private val Schema = "public"

  private val ColumnsQuery =
    """select column_name, data_type, is_nullable
      |from information_schema.columns
      |where table_name = ? and table_schema = ?
      |order by ordinal_position""".stripMargin

  def liveColumns(conn: Connection, table: String): List[ColumnContract] = {
    val ps = conn.prepareStatement(ColumnsQuery)
    try {
      ps.setString(1, table)
      ps.setString(2, Schema)
      val rs = ps.executeQuery()
      try
        Iterator
          .continually(rs)
          .takeWhile(_.next())
          .map(r => ColumnContract(r.getString(1), r.getString(2), r.getString(3) == "YES"))
          .toList
      finally rs.close()
    } finally ps.close()
  }

  def verify(conn: Connection, ref: TableRef): List[String] = {
    val live = liveColumns(conn, ref.name).map(c => c.name -> c).toMap
    if (live.isEmpty) {
      List(s"table '${ref.name}' not found in schema '$Schema' (or has no columns)")
    } else {
      ref.columns.flatMap { exp =>
        live.get(exp.name) match {
          case None =>
            Some(s"${ref.name}.${exp.name}: missing (expected ${exp.dataType})")
          case Some(act) =>
            val problems = List(
              Option.when(act.dataType != exp.dataType)(s"type '${act.dataType}' != expected '${exp.dataType}'"),
              Option.when(!exp.nullable && act.nullable)("is NULLABLE but contract requires NOT NULL"),
            ).flatten
            Option.when(problems.nonEmpty)(s"${ref.name}.${exp.name}: ${problems.mkString("; ")}")
        }
      }
    }
  }

}
