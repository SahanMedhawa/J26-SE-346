-- Separate Flyway location/history; legacy public analytics tables are untouched.
CREATE SCHEMA IF NOT EXISTS analytics_private;
REVOKE ALL ON SCHEMA analytics_private FROM PUBLIC;

CREATE TABLE analytics_private.streams (
  stream_key varchar(64) PRIMARY KEY,
  stream_tuple text NOT NULL,
  project_id uuid NOT NULL,
  repository_id text NOT NULL,
  branch_ref text NOT NULL,
  source_system text NOT NULL,
  provenance text NOT NULL CHECK (provenance IN ('DEMO', 'UPSTREAM')),
  dataset text NOT NULL,
  CHECK ((provenance = 'DEMO' AND dataset <> '') OR (provenance = 'UPSTREAM' AND dataset = ''))
);

CREATE TABLE analytics_private.runs (
  id uuid PRIMARY KEY,
  stream_key varchar(64) NOT NULL REFERENCES analytics_private.streams(stream_key),
  external_run_id text NOT NULL,
  analysis_sequence bigint NOT NULL CHECK (analysis_sequence >= 0),
  analyzed_at timestamptz NOT NULL,
  commit_ref text NOT NULL,
  contract_version integer NOT NULL CHECK (contract_version = 1),
  identity_version text NOT NULL CHECK (identity_version = 'identity-v1'),
  rule_set_version text NOT NULL,
  detector_version text NOT NULL,
  mapping_version text NOT NULL,
  scope_version text NOT NULL,
  analyzed_scope text[] NOT NULL CHECK (cardinality(analyzed_scope) > 0),
  analysis_status text NOT NULL CHECK (analysis_status IN ('PENDING','COMPLETE','PARTIAL','FAILED','SKIPPED')),
  result_mode text NOT NULL CHECK (result_mode = 'SNAPSHOT'),
  pipeline_outcome text,
  gate_outcome text,
  loc bigint CHECK (loc >= 0), -- when present, validated against exactly analyzed_scope
  payload_hash varchar(64) NOT NULL,
  canonical_payload text NOT NULL,
  occurrence_count integer NOT NULL CHECK (occurrence_count >= 0),
  UNIQUE(stream_key, external_run_id),
  UNIQUE(stream_key, analysis_sequence)
);

CREATE TABLE analytics_private.identities (
  identity_key varchar(64) PRIMARY KEY,
  identity_namespace text NOT NULL,
  structural_tuple text NOT NULL,
  upstream_id text,
  rule_key text NOT NULL,
  entity_type text NOT NULL,
  entity_key text NOT NULL,
  finding_kind text NOT NULL,
  source_key text NOT NULL,
  target_key text,
  relation text
);

CREATE TABLE analytics_private.occurrences (
  run_id uuid NOT NULL REFERENCES analytics_private.runs(id),
  identity_key varchar(64) NOT NULL REFERENCES analytics_private.identities(identity_key),
  rule_key text NOT NULL,
  rule_name text NOT NULL,
  entity_type text NOT NULL,
  entity_key text NOT NULL,
  entity_name text NOT NULL,
  severity integer NOT NULL CHECK (severity BETWEEN 1 AND 3),
  display_location text,
  message text,
  PRIMARY KEY(run_id, identity_key)
);

CREATE INDEX streams_project ON analytics_private.streams(project_id);
CREATE INDEX runs_stream_time ON analytics_private.runs(stream_key, analyzed_at);
CREATE INDEX occurrences_entity ON analytics_private.occurrences(entity_type, entity_key, run_id);
CREATE INDEX occurrences_identity ON analytics_private.occurrences(identity_key, run_id);
REVOKE ALL ON ALL TABLES IN SCHEMA analytics_private FROM PUBLIC;
