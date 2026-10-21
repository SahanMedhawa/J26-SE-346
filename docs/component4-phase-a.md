# Component 4 Phase A: snapshot ingestion

Implemented locally on 2026-10-20. Phase A only; no lifecycle, hotspot, trend,
health-score or UI implementation. The pasted Phase A request supersedes the
earlier plan's phase ordering and late-arrival recomputation proposal. Late runs
are rejected. Future lifecycle labels must be NEW, RESOLVED, PERSISTING (the old
plan's PERSISTENT wording is superseded). Upstream contracts remain provisional.

## Entry points and boundaries

- Shared, framework-free `akegp.shared.analytics.AnalysisSnapshot` and
  `AnalyticsIngestion` define version 1 input and receipts.
- `SnapshotIngestionService` requires a repository, trusted adapter source and
  `ProjectWriteAuthorizer`. Authorization is checked before validation/storage.
- `SnapshotNormalizer` validates, deduplicates and produces canonical content.
- `JdbcSnapshotRepository` owns one transaction per run, including stream,
  identity and occurrence inserts. It uses its explicitly supplied datasource.
- `ModelsDemoFixture` supplies five deterministic snapshots to that same service.
  It never writes derived metrics or lifecycle statuses.
- `LocalDemoImport` is a manually invoked development entry point. There is no
  Spring bean, HTTP ingestion route, automatic migration, startup seed or reset.

Existing project IDs are UUIDs. No new project registry, auth system or edits to
other members' modules were introduced. The existing platform has no implemented
project-membership authorization to reuse. Consequently there is no production
adapter: it must supply authenticated project authorization before exposing this
port. The local importer permits only the fixed constructed demo project. Merely
supplying a project ID does not establish authorization.

## Contract and normalization

Required run fields: contract version 1, project UUID, canonical repository and
branch, external run ID, nonnegative upstream sequence, analysis timestamp,
commit reference, rule-set/detector/severity-mapping/scope versions, explicit
analyzed scope, analysis status, SNAPSHOT mode and a non-null violations array.
Pipeline/gate outcomes and matching-scope LOC are optional. LOC is never generated;
zero is retained as a measurement, not a valid future density denominator.

Scope is an unordered set of explicit entity-type/entity-key tokens, built with
`SnapshotNormalizer.scopeEntity(type, key)`. Every finding's affected entity must
be included. Adapters must declare all covered entities, including clean ones.
Scope version describes additional analysis configuration; adapters must change
it when coverage semantics change. Required canonical strings reject whitespace
padding; normalization does not guess aliases or rename entities.

Findings contain rule key/name, affected entity type/key/name, finding kind,
semantic source key, optional target/relation pair, severity 1..3 and optional
display location/message. Numeric mapping is explicit: low=1, medium=2, high=3;
the adapter must agree and version its translation. No implicit mapping from C3's
labels or CRITICAL is implemented.

Identity v1 is SHA-256 of an unambiguous length-prefixed tuple. Its namespace
contains project/repository/source/provenance/dataset. Structural identity adds
rule/entity/kind/source/target/relation. It excludes branch, severity, line,
message, timestamp, commit and run ID. A verified-stable upstream ID can replace
the structural key only when the server-side adapter explicitly enables it.
Readable structural fields are retained and checked against stored identities;
conflicting reuse fails. Renames to structural keys produce new identities;
there is no heuristic rename tracking or mapping migration.

Provenance and dataset are absent from caller input. They come from the trusted
adapter configuration; DEMO and UPSTREAM cannot share a stream or identity
namespace. Receipts label demo results `Demo snapshot - not upstream analysis`.

Canonical replay equality sorts scope and identities, removes identical
duplicates and includes all run metadata and historical finding evidence.
Changing severity, names, evidence, coverage or other stored content under an
existing external run ID is a conflict. Display locations do not change identity,
but changing them on a replay is an immutable-payload conflict. This conservative
policy avoids silently discarding corrected evidence. Hash and canonical text
are both compared. Optional absent text differs from an empty string.

COMPLETE with zero violations is valid, even if the gate or pipeline failed.
PENDING/PARTIAL/FAILED/SKIPPED snapshots remain explicit non-complete observations;
they must never establish resolution. Missing status or findings is rejected.
DELTA is rejected, including when it has an empty array. No completeness is
inferred from a successful gate or from finding count. Adapters are responsible
for the truth of the declared coverage/completeness; C4 cannot detect an omitted
finding in an allegedly complete upstream snapshot.

## Persistence and ordering

New Flyway location: `classpath:db/migration/analytics`; schema and history:
`analytics_private.analytics_schema_history`. The migration leaves the legacy
public V1/V2 tables and existing Flyway locations untouched. No backfill is made.
Tables: streams, runs, identities, occurrences. Runs retain versions, scope,
outcomes and canonical payload. Occurrences snapshot severity and rule/entity
display metadata. Structural identity metadata is immutable.

Stream identity includes project/repository/branch/source/provenance/dataset.
Run ID and sequence are independently unique within that stream. Stream-row
locking serializes writers, including concurrent first imports. An identical
replay is returned before the sequence check, even after later runs exist.
Otherwise sequence must exceed the highest stored sequence, including incomplete
attempts. Late arrivals and sequence reuse are rejected; no suffix recomputation
or partial-run finalization is supported. A corrected/finalized attempt needs a
new external ID and later upstream sequence. Arrival time is never history order.

Future comparisons must additionally match rule-set/detector/mapping/scope and
identity versions and explicit scope; those fields deliberately do not partition
ordering. New coverage cannot bypass late-arrival protection. Phase B must define
baseline transitions and visible incomplete gaps before calculating any results.

The schema revokes PUBLIC access. Keep it outside Supabase's exposed schemas and
grant only the backend role the required access; verify actual inherited/default
grants before deployment. Owner access does not replace project authorization.
No live Supabase configuration or grants have been tested. No Timescale SQL or
extension is used. PostgreSQL, JDBC and Flyway dependencies match existing module
patterns. Runtime composition is deliberately explicit to avoid adding another
auto-selected datasource to the existing application.

## Local setup and commands (database steps not executed)

Use a **dedicated, disposable, local** PostgreSQL database named `c4_demo` for the
demo and `c4_test` for integration tests. Confirm the target before any migration
or import. No command creates, resets or drops a database. Do not use a port
forward to a remote/shared database: a loopback address alone cannot prove local
ownership. The local tools reject remote hosts, wrong database names, user info
and JDBC URL query options.

Backend environment names only:

- Demo: `C4_ENV` must be `development`; `C4_DB_URL`, `C4_DB_USER`, `C4_DB_PASSWORD`.
- Tests: `C4_TEST_DB_URL`, `C4_TEST_DB_USER`, `C4_TEST_DB_PASSWORD`.

No `.env` is read by these entry points. Never put credentials in commands, logs,
committed files or browser code. These names are not wired into normal app startup.

From the repository root, compile and run the default no-database suite:

```powershell
mvn -o -pl akegp-analytics -am test
```

After separate approval for the disposable local test database, opt into tests
that execute the migration and write test records (retained; no cleanup/reset):

```powershell
mvn -o -pl akegp-analytics -am '-Dc4.localDbTests=true' '-Dtest=JdbcSnapshotRepositoryTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

For a manual demo, construct a runtime classpath using Maven's dependency plugin
(plugin must already be cached for offline use). These preparation commands were
not executed; they install artifacts into the local Maven cache, not a server:

```powershell
mvn -o -pl akegp-analytics -am -DskipTests install
mvn -o -pl akegp-analytics dependency:build-classpath '-Dmdep.outputFile=target/runtime-classpath.txt'
$c4Classpath = 'akegp-analytics/target/classes;' + (Get-Content -Raw akegp-analytics/target/runtime-classpath.txt).Trim()
```

Only after approval for `c4_demo`, with the backend environment set securely:

```powershell
java -cp $c4Classpath akegp.analytics.ingestion.LocalDemoImport migrate --confirm-local-demo
java -cp $c4Classpath akegp.analytics.ingestion.LocalDemoImport import --confirm-local-demo
```

Re-running import returns replays. Migrate and import are separate commands so
import cannot silently migrate. The five snapshots have 3, 2, 2, 3, 5 occurrences;
A=3, B=2, C=2, D/E/F=1. Fixed dates are January 1-5, 2026 at 12:00 UTC, with
reference time January 5 at 12:00 UTC. All project/repository/rule/anchor/commit
metadata is constructed DEMO metadata. No LOC is present. This is simulated
input integration; no real C2/C3 interface has been connected.

## Verification and remaining work

Executed `mvn -o -pl akegp-analytics -am test`: BUILD SUCCESS; 18 tests reported,
13 passed (12 new pure tests plus the pre-existing smoke test), five database
tests skipped, zero failures/errors. Maven emitted platform-encoding warnings
and a launcher `Access is denied` line, but compiled and completed with exit 0.
No dependencies were downloaded. `docker ps --format '{{.Names}} {{.Image}} {{.Ports}}'`
listed the existing project stack; no disposable `c4_test` target was confirmed.
The first sandboxed Docker inventory failed; an approved read-only retry succeeded.

`git diff --check -- akegp-analytics akegp-shared-kernel docs/component4-phase-a.md`
passed; new files were additionally checked with
`git -c core.autocrlf=false diff --no-index --check -- NUL <file>` and had no
whitespace diagnostics. The repository-wide `git diff --check` reports a
pre-existing trailing blank line at `akegp-app/src/main/resources/application.yml:41`.
That unrelated edit was left intact. `.gitignore` now has a narrow Analytics
Java config exception, matching the existing Rules module convention.

Database tests cover
replay/conflict, sequence rejection, duplicate normalization, zero runs, retained
historical severity, identity-conflict rollback, incomplete status, branch
isolation, concurrent replay and fixture reruns. They require explicit opt-in.
Pure tests cover contract validation, canonicalization, identity stability,
scope/LOC, authorization denial, provenance isolation and fixture contents.

No migration or demo import has been executed. No application startup, frontend
change, external call to an upstream system, commit, push or deployment is part
of this phase. Existing edits to application.yml, docker-compose.yml and the
previously created plan were preserved. Health scoring remains deferred.

AI-use entry: Codex generated the Phase A Java contract, validator, JDBC storage,
SQL migration, development fixture/importer, tests and this setup document. Only
actually executed checks are reported; no architect feedback, live integration,
latency measurement or upstream approval is claimed.

Next authorization: Phase B lifecycle/drift work. Actual database execution still
requires confirmation of a disposable target. Production adapter composition
requires an implemented project-authorization source and agreed upstream identity,
completeness, sequence, coverage and version guarantees.
