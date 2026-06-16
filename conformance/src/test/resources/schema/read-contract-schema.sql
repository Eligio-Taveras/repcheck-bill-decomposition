-- DC-4 read-contract schema snapshot.
--
-- A faithful, self-contained DDL for ONLY the tables the decomposition pipeline READS from the bills pipeline
-- (raw_bill_text, bill_text_versions). The bills pipeline (repcheck-data-ingestion, db-migrations-runner) OWNS the real
-- schema; this is a consumer-side copy that the DC-4 CI parity gate applies to a throwaway pgvector container and then
-- runs SchemaContractSpec against. Column types / nullability / enum + vector definitions were reproduced from the live
-- AlloyDB via information_schema + pg_enum introspection.
--
-- REFRESH when upstream legitimately evolves: re-introspect the live DB and update this file in a reviewed PR — the diff
-- IS the drift signal. (pg_dump -t omits the dependent CREATE TYPE / CREATE EXTENSION, so they are kept here by hand.)

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TYPE text_version_code_type AS ENUM (
  'IH','IS','RH','RS','RFS','RFH','EH','ES','ENR','CPH','CPS','PCS','PCH','PL','RDS',
  'RDH','RTS','RTH','ATS','ATH','PP','RCH','EAS','RIS','EAH','LTH','LTS','PRL','RCS','RIH'
);

CREATE TYPE format_type_enum AS ENUM ('Formatted Text', 'PDF', 'Formatted XML');

CREATE TABLE raw_bill_text (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  bill_id     bigint  NOT NULL,
  version_id  bigint,
  chunk_index integer NOT NULL,
  content     text    NOT NULL,
  created_at  timestamptz NOT NULL DEFAULT now(),
  embedding   vector(1024)
);

CREATE TABLE bill_text_versions (
  bill_id      bigint NOT NULL,
  version_code text_version_code_type NOT NULL,
  version_type text,
  version_date timestamptz,
  format_type  format_type_enum,
  url          text,
  fetched_at   timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now(),
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY
);
