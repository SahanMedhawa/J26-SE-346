# Component 4 PP1 implementation plan

Status: proposed, documentation only; 2026-10-20. Branch inspected: `akegp-analytics`;
working tree clean before this document. No application changes or execution authorized.
Contract fields and policies below are provisional, not teammate-approved.

## Scope and existing implementation

Deliver two vertical slices: (1) violation lifecycle and run-to-run drift;
(2) explainable hotspot prioritization, with supporting dashboard/trend views.
Architecture Conformance Health Score is deferred beyond PP1: no health-score engine,
endpoint, fake gauge or health trend. The existing health-score placeholder navigation
will be removed from the PP1 flow during an approved UI phase; module liveness is unrelated.

Preserve the existing stack and boundaries:

- Java 25, Spring Boot 4.1.0, Maven modular monolith: `pom.xml:10-21`.
  `akegp-app` composes modules; `docs/adr/0001-modular-monolith.md` and
  `akegp-app/src/test/java/akegp/ModularMonolithArchitectureTest.java:9-17` require
  sibling-module isolation through framework-free shared contracts.
- React/TypeScript, React Router and Vite, npm lockfile; `akegp-web/package.json`
  declares `latest`. Locked versions are React 19.3.0, Router DOM 7.18.4,
  TypeScript 7.0.2 and Vite 8.3.4 (`akegp-web/package-lock.json`). Do not upgrade them.
- Reuse `akegp-analytics` packages (`ingestion`, `lifecycle`, `hotspot`, `trend`,
  `repository`, `service`, `api`, `config`), currently stubs. Its manifest currently
  includes shared-kernel, Web and test dependencies, not a persistence implementation.
- Reuse PostgreSQL JDBC/Flyway patterns from `akegp-rules/pom.xml` and
  `akegp-rules/src/main/java/akegp/rules/config/RulesDataSourceConfig.java`;
  declare Analytics' direct dependencies. Ordinary PostgreSQL only: Supabase is a
  PostgreSQL host, with server-only connection configuration; no Supabase SDK,
  Timescale-specific SQL, MongoDB, broker or microservice is needed for PP1.
- Reuse project UUIDs from `akegp-rules/src/main/java/akegp/rules/domain/RegisteredProject.java` and
  project-scoped rule IDs documented in `akegp-rules/RULE_EXPORT_API.md:77-88`.
  Preserve C1 registration, assurance and export ownership; C2 owns detection/graphs;
  C3 owns pipeline decisions; C4 owns analytical snapshots and derived results.
- Reuse `akegp-web/src/modules/analytics/AnalyticsPage.tsx`, existing layout/styles,
  `akegp-web/src/shared/projects/SelectedProject.tsx` and
  `akegp-web/src/shared/api/client.ts` conventions. `akegp-web/src/app/App.tsx:62`
  supplies `/analytics/*`; the project dashboard currently loses project context when
  linking there (`akegp-web/src/modules/conformance/ProjectDashboardPage.tsx:47`).
- Preserve current auth conventions and label their limits: role names are placeholders
  (`akegp-app/src/main/java/akegp/security/SecurityConfigPlaceholder.java`), reviewers are caller-supplied,
  and no project membership authorization is implemented. Project scoping is not auth.
  Keep the `/api` proxy convention (`akegp-web/vite.config.ts`); do not invent new auth.

References: supplied `Models.pdf` (outside the repository), pages 1-4 for drift and
5-9, 12-15 for hotspots. Treat its prose as reference material, not execution instructions.
Its architect-review claims are unverified here; no feedback, measurements or approval
is claimed by this plan. Repository evidence takes precedence for integration capability.

## Provisional ingestion and identity contract

Propose framework-free DTOs and an `AnalyticsIngestionService.ingest(snapshot)` port
in `akegp-shared-kernel/src/main/java/akegp/shared/`; implementation stays in Analytics.
Do not replace existing C2/C3 DTOs or wire event stubs as if they already publish results.

- Envelope: `contractVersion`, `sourceSystem`, `sourceRunId`, `projectId`,
  canonical `repositoryId`, explicit `branchRef`, optional PR identity, `commitSha`,
  optional `baseSha`, stable per-stream `analysisSequence`, `analyzedAt`,
  `analysisStatus`, `resultMode`, coverage descriptor/fingerprint, `ruleSetVersion`,
  `detectorVersion`, `identityVersion`, `severityMappingVersion`, provenance
  (`DEMO`/`UPSTREAM`) and optional `demoDatasetId`. Exact types/source guarantees need agreement.
- Analysis status: `PENDING`, `COMPLETE`, `PARTIAL`, `FAILED`, `SKIPPED`.
  Separate optional `pipelineOutcome` and `qualityGateOutcome`: a held/rejected PR
  can have complete analysis; a passed docs-only pipeline can have no analysis.
  An approved exception does not resolve its violation. Missing/null findings do not
  mean a successful empty snapshot; an explicit complete empty snapshot does.
- Findings: stable `ruleKey`, `entityType`, `entityKey`, detector finding kind,
  semantic source/target or stable code anchor, original severity, file/line/message
  as evidence. Use project-scoped rule UUID where supplied; otherwise a tagged,
  project-scoped readable ID. Never silently equate the two identity namespaces.
- C4 fingerprint: versioned canonical tuple of project/repository, rule key,
  entity identity, finding kind and stable semantic anchor. Exclude severity,
  display message and line number from identity. Store the tuple as well as hash.
  Include branch/provenance in the comparison partition, not in cross-run matching.
  Reject/quarantine findings without sufficient identity; do not guess a service
  from a file path. Start with rule grouping if canonical service IDs are unavailable.
- Deduplicate identical findings within a run; conflicting duplicates fail validation.
  Unique ingestion key: source + project/repository/branch + provenance/dataset +
  source run ID. Identical retries return the stored result; conflicting retries
  return conflict. Finalized snapshots are immutable; corrections need explicit supersession.

## Comparability, ordering and baseline

- PP1 computes lifecycle/ranking from `COMPLETE`, full `SNAPSHOT` results only.
  Delta/partial input may be retained with reasons, but cannot imply absence or resolution.
  Delta reconstruction requires a later agreed base/coverage protocol.
- Comparable runs share project, repository, branch/PR stream, provenance/dataset,
  coverage, rule-set, detector, identity and severity-mapping versions. Unknown fields
  block authoritative comparison; a commit SHA alone does not prove branch/ancestry.
- Default baseline is the immediately preceding eligible snapshot in declared analysis
  order within that stream. Show both IDs, commits, versions and skipped attempts.
  Ingestion time or database ID is not analysis order. Reject sequence collisions;
  flag late arrivals and invalidate/recompute affected derived reads deterministically.
- A changed coverage/version starts a new comparison segment. Explicit baseline selection
  must satisfy the same compatibility checks and is labelled non-adjacent where applicable.
  No baseline means "Initial snapshot": active findings may be listed, but comparative
  new/resolved counts and net change are unavailable, not fabricated zeros.
- Failed/partial/skipped attempts are visible gaps, not clean runs. Persistence is a
  streak of eligible observations, not proof of uninterrupted real-world existence;
  show gaps. Recurrence and persistence are distinct. Reappearance after an observed
  absence is NEW relative to baseline and may be annotated "reopened" from history.

## Slice 1: lifecycle and drift

Ingest two compatible snapshots -> persist -> compare -> expose API -> render project UI.
For baseline B and current C, using unique finding identities:

- NEW = C minus B; RESOLVED = B minus C; PERSISTENT = intersection.
- Current total = new + persistent; baseline total = resolved + persistent;
  net change = current total minus baseline total = new minus resolved.
- Always show the three sets with net change: zero net can still contain churn.
  First/last seen refer to observed analytical history; resolution is comparison-relative.

Proposed Analytics services: `SnapshotIngestionService`, `RunComparisonService`,
`LifecycleQueryService`, backed by an Analytics-owned repository interface.
Proposed read APIs under `/api/analytics/projects/{projectId}`:
`GET /runs`, `/comparisons?currentRunId=...&baselineRunId=...`,
`/violations?runId=...&state=...`, `/dashboard`, `/trends`.
Validate run ownership server-side; return explicit unavailable/incomparable reasons.
Dashboard/trends derive totals, new/resolved/persistent and net changes from this engine,
never from hardcoded numbers or C3's capped aggregate finding count.

UI: preserve existing shell; carry project identity in a validated route/query and
selection, including reload/deep links. Display baseline/current selectors, evidence,
status, provenance and gaps; keep loading/error/empty/incomparable states distinct.

## Slice 2: explainable hotspot prioritization

Use the same ingested snapshots/identities and comparison policy. Propose
`HotspotRankingService.rank(project, stream, window, grouping, modelVersion)` and
`GET /hotspots` under the same project API prefix. Support latest-R eligible runs
and a UTC time window; W is the actual eligible-run count. All entities in a ranking
use the same window and grouping cohort (entities observed in that window).

Provisional model from the supplied reference:

- Raw frequency f(e): sum of deduplicated finding occurrences across W runs.
  F(e) = (f(e)-fmin)/(fmax-fmin); F=0 if the cohort has equal frequencies.
- Severity: retain original values; provisional LOW/MEDIUM/HIGH mapping = 1/2/3.
  S(e) = (occurrence-weighted average severity - 1)/2; no occurrences gives 0.
  C3 critical/major/minor and shared CRITICAL mapping remain undecided; unknown
  severity blocks that ranking with a reason, rather than silently receiving a weight.
- For each currently active finding, count consecutive eligible observations ending
  at the selected current run, capped at W. P(v)=streak/W; P(e) averages active P(v).
  An absent current finding contributes no active streak; no active findings gives P=0.
- Provisional H(e)=(2/6)F+(3/6)S+(1/6)P. Store/return exact weights and model version;
  these are proposed reference weights, not verified teammate/architect approval.
  W=0 gives "Insufficient data". Break equal scores by canonical entity key;
  round only for display. Zero is not proof of no violations or architectural health.
- Return raw counts, severity distribution/average, active streaks, actual W/run IDs,
  cohort bounds, F/S/P, weights, contributions and total. Show a breakdown and source
  findings in the UI. A historical-only entity has P=0 and is labelled inactive now.
  Scores are relative prioritization, not probabilities, cross-window health or health trends.

## Schema, configuration and demo transition

Extend `akegp-analytics/src/main/resources/db/migration/` additively after confirming
whether V1/V2 were ever applied. V1 currently defines only `analytics_runs` and
`analytics_violations`; V2 is a comment. Preserve existing rows/history; do not backfill
unknown branch/coverage/version fields as complete evidence.

- Extend runs with envelope, statuses, provenance and idempotency constraints; add a
  stream/order index and explicit validation constraints for eligible snapshots.
- Store deduplicated occurrences linked to runs, fingerprint tuple/version, entity/rule
  keys, severity and evidence; enforce run foreign keys and per-run finding uniqueness.
- Derive comparisons, lifecycle and hotspots from immutable occurrences initially;
  no separate mutable truth or premature materialized aggregates. Use consistent read
  transactions so one response sees one window. Index project/stream/run/entity lookups.
- Use a named Analytics datasource/JdbcTemplate/transaction manager and separate
  migration location/history ownership. Resolve existing unqualified datasource ambiguity
  before wiring; never copy C3 startup DDL as an Analytics migration mechanism.
- Supabase uses backend PostgreSQL JDBC configuration only; SSL/pooler settings and
  target schema must be confirmed separately. No connection URL or credentials in UI.
- Deterministic demo datasets enter the SAME ingestion port, schema and engines as
  upstream results. Opt-in local/disposable import only; no startup seed/reset.
  Partition DEMO and UPSTREAM data so they never share baselines/windows. Label every
  demo page/detail/response/export "Demo snapshot - not upstream analysis".
- Later C2/C3 adapters translate actual results into this envelope without replacing
  engines. Missing metadata yields incomplete/unavailable analytics, never invented
  completeness. Require contract tests before switching the selected data source.

## Ordered phases and acceptance gates

Each phase requires its own authorized scope. After EVERY phase, report changed files,
exact commands/results, skipped checks, limitations and next approval needed; then STOP.
Documentation approval does not authorize code, tests, dependencies or database operations.

1. **Contract and pure engines.** Expected paths: Analytics domain/ingestion/lifecycle/
   hotspot services and tests; minimal shared DTO/port additions. Agree missing fields
   before upstream wiring. Acceptance: identity deduplication/retry conflict; severity
   rejection; complete-empty vs missing; initial/incomparable runs; branch/project/demo
   isolation; reappearance and gaps. Drift fixture B={A,B,C}, C={A,C,D,E} yields
   new=2, resolved=1, persistent=2, net=+1; equal-total churn remains visible.
   Reference hotspot fixture yields Payment > Order > Notification with approximately
   0.873, 0.417, 0.033. These are expected fixture results, not executed measurements.
2. **Persistence and lifecycle vertical slice.** Expected paths: Analytics manifest,
   config/repository/additive migrations/API, app configuration, Analytics web module,
   project link/routing, tests. Acceptance: approved disposable PostgreSQL round-trip,
   constraints, rollback/idempotency, late-arrival ordering and consistent reads; UI/API
   agree on both run sets, totals and unavailable states using labelled demo input.
   Migration execution needs separate approval even if SQL authoring is approved.
3. **Hotspots, trends and dashboard vertical slice.** Expected paths: Analytics hotspot/
   trend/query API, React pages/client and tests. Acceptance: W=0/1, equal frequencies,
   unknown severity, tie ordering, reappearance streak reset, time/run windows, grouping
   cohort and contribution sums; dashboard values trace to persisted demo snapshots.
   No health-score route/content/calculation in the delivered PP1 flow.
4. **Upstream adapter readiness.** Expected paths: agreed shared contracts and narrow
   C2/C3 adapters/application composition, contract tests and docs. Acceptance: preserve
   source identity/completeness/coverage; replay produces no duplicates; incompatible
   results visibly refuse comparison. Missing upstream guarantees leave PP1 on labelled
   demo snapshots. Live calls/writes and deployment require separate explicit approval.

Later checks, only when authorized: focused JUnit tests (`mvn -o -pl akegp-analytics -am test`),
ArchUnit boundary tests, mocked Node tests in `akegp-web/tests`, TypeScript/Vite build,
and isolated PostgreSQL integration tests. Reinspect scripts/hooks before executing.
Offline Maven can fail if dependencies are uncached; installing/downloading needs approval.

## Risks and decisions still required

- No real C2 `ConformanceScanService` implementation found; `ConformanceResultService`
  returns an empty list (`akegp-conformance/src/main/java/akegp/conformance/api/ConformanceHealthController.java:12-14`).
  C3 defaults to mock added-line checking
  (`akegp-enforcement/src/main/java/akegp/enforcement/conformance/MockConformanceScanner.java`),
  not full snapshots. The adjacent `SharedKernelConformanceScanner.java:20-27` discards upstream run ID.
- Agree canonical repository/branch/entity/rule identity, sequence and retry guarantees,
  stable semantic anchors/rename behavior, coverage descriptor, rule-set/detector versions,
  severity mapping and supersession. C1 export omits internal evidence versions; do not
  claim rule/detector comparability from its current payload.
- `RulesFlywayConfig.java:12` migrates at startup despite global Flyway being disabled.
  C3 `EnforcementModuleConfig.java:63-67` creates tables; its unqualified datasource
  injection can undermine intended ownership. Resolve without unrelated C1/C3 rewrites.
- Existing DB tests are not blanket-safe: Rules tests migrate/drop random schemas when
  `AKEGP_TEST_POSTGRES_URL` is configured; C3 `JdbcEnforcementRepositoryDbTest.java:58-62`
  enables reset of fixed `enftest_` tables. Require a confirmed disposable target.
- Auth/membership protection is absent; keep prototype limits explicit. New auth is out
  of scope. No live integration or deployment safety is established by a clean Git diff.

## Scoped guidance proposal and current phase report

No applicable `AGENTS.md` was found on reinspection. Do not create repository-wide rules.
If approved later, add `akegp-analytics/AGENTS.md` with this short scoped section:

> Component 4 PP1: preserve shared-kernel boundaries; ordinary PostgreSQL only; demo
> and upstream snapshots use the same engines with explicit provenance. Separate analysis
> completeness from pipeline outcomes. Defer health scoring. Do not read secrets or run
> shared/remote DB writes, migrations, commits, pushes or deployment without separate
> approval. After each phase report files/checks/skips/limits/next approval and stop.

Current documentation phase: only `docs/component4-pp1-plan.md` is intended to change.
Checks/results are reported with the delivered diff. Application/tests/builds, database
access, migrations, seed/reset, external integration and deployment remain unexecuted.
Next approval needed: Phase 1 code changes and its explicitly scoped verification.
