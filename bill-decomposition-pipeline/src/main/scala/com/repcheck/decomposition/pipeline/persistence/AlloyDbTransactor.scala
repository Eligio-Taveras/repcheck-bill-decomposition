package com.repcheck.decomposition.pipeline.persistence

import scala.concurrent.ExecutionContext

import cats.effect.{Async, Resource}

import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor

import com.repcheck.decomposition.pipeline.config.DatabaseConfig

/** A HikariCP-pooled Doobie [[Transactor]] over the AlloyDB / Cloud SQL Postgres connection. */
object AlloyDbTransactor {

  def make[F[_]: Async](config: DatabaseConfig): Resource[F, Transactor[F]] =
    HikariTransactor.newHikariTransactor[F](
      driverClassName = "org.postgresql.Driver",
      url = s"jdbc:postgresql://${config.host}:${config.port}/${config.database}",
      user = config.user,
      pass = config.password,
      connectEC = ExecutionContext.global,
    )

}
