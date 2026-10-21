# Component 4 PP1 implementation and verification

Updated 2026-10-20. Continues the Phase A ingestion work and the provisional
`component4-pp1-plan.md` following authorization to implement Phase B onwards
using Neon PostgreSQL. No health-score engine or health-score UI is included.

## Delivered

- Phase B: immutable historical reads, exact NEW/RESOLVED/PERSISTING comparisons,
  optional explicit baselines, violation counts and trends, and explainable
  service/module/rule hotspot rankings.
- Phase C: project-scoped HTTP queries and React dashboard, lifecycle and hotspot
  views, with run selection, filtering, pagination, date/run windows, factor
  details, and separate loading, error, empty and baseline states.
- Phase D: engine/API/frontend tests, disposable PostgreSQL integration tests,
  Neon migration and fixture verification, and local browser verification.

Comparison requires complete compatible snapshots. An incomplete or incompatible
intervening run starts a new baseline and breaks the hotspot segment; the engine
does not bridge that gap. First-run deltas are unavailable, not zero. Zero net
change can still contain both new and resolved findings.

Hotspots use F=2/6, S=3/6 and P=1/6. Severity is occurrence-weighted; persistence
uses the current streak divided by actual selected W. Frequency normalization
uses the entire selected cohort before pagination, with F=0 for an equal-frequency
cohort. Historical inactive entities remain represented. Responses expose the
window, cohort, raw factors, weights and contributions. Date bounds are UTC
`[from,to)`; compatibility is checked against intervening history.

## Database and access

The dedicated Analytics pool uses server-side `AKEGP_ANALYTICS_DB_URL`,
`AKEGP_ANALYTICS_DB_USER` and `AKEGP_ANALYTICS_DB_PASSWORD`. The command scripts
load only those keys from repository `.env`; environment/system values take
precedence. Normal application startup expects these settings supplied to its
environment; it does not automatically import the repository `.env`.

The Neon command requires a JDBC PostgreSQL Neon hostname and SSL mode
`require` or `verify-full`. Credentials are not printed by the helper.
Migration is explicit and confined to `analytics_private`, including its own
Flyway history. Application startup neither migrates nor seeds.

The default read authorizer denies real projects. Local Docker Compose explicitly
sets `AKEGP_ANALYTICS_DEVELOPMENT_ACCESS=true` and
`AKEGP_ANALYTICS_DEMO_ENABLED=true`. Its application composition adapter permits
registered project IDs and the explicitly enabled fixed demo; unknown IDs are
denied. This development policy does not authenticate users. Set development
access to false for any deployment that requires membership enforcement.
An empty registered project returns an empty run list and the UI offers an
explicit link to the labelled demo. It never imports demo findings into that project.
A host-provided
`ProjectReadAuthorizer` must enforce authenticated project membership before
production use. Explicit `akegp.analytics.demo-enabled=true` permits only the
fixed constructed demo project, with reads additionally restricted to its DEMO
dataset. Project UUID scoping alone is not authorization.

The shared ingestion port exists, but real C2/C3 publishers and contract agreement
are still pending. Demo data is clearly labelled and is never an error fallback.

## Reproduce

From the repository root:

```powershell
mvn -o -pl akegp-analytics -am test
./akegp-analytics/scripts/neon.ps1 -Operation check -ConfirmNeonAnalytics
./akegp-analytics/scripts/neon.ps1 -Operation migrate -ConfirmNeonAnalytics
./akegp-analytics/scripts/neon.ps1 -Operation demo -ConfirmNeonAnalytics
./akegp-analytics/scripts/neon.ps1 -Operation verify -ConfirmNeonAnalytics
```

The scripts use the generated SnapshotIngestionTest runtime classpath, so run the
tests first and do not run them while compilation is rewriting the classes.
Migration/demo operations write only the explicitly selected Analytics target.
Identical demo imports replay existing immutable runs.

For the read-only local demo server:

```powershell
./akegp-analytics/scripts/preview.ps1
```

In a second terminal, from `akegp-web`:

```powershell
$env:BACKEND_URL='http://127.0.0.1:8087'
npm run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

Open
`http://127.0.0.1:5174/analytics/dashboard?projectId=c4000000-0000-4000-8000-000000000001`.
The preview binds to loopback, enables the fixed demo only, and does not migrate
or import. This is a separate local preview; the existing Docker application was
not redeployed.

## HTTP surface

All query routes are beneath `/api/analytics/projects/{projectId}`:
`/runs`, `/comparisons`, `/violations`, `/dashboard`, `/trends`, `/hotspots`.
Comparison queries accept `currentRunId`, optional `baselineRunId`, lifecycle
`state`, `offset`, and `limit`. Hotspots accept `currentRunId`, `grouping`,
`runs` or UTC `from`/`to`, and pagination. Authorization failures return 403;
unavailable database configuration returns a sanitized 503.

## Verification evidence

- Analytics: 31 tests passed with no skips against disposable PostgreSQL 16,
  including five JDBC integration tests. Coverage includes rollback, immutable
  replay/conflict, concurrency, sequence ordering, complete empty snapshots,
  historical metadata and golden fixture calculations.
- Frontend: 64 tests passed across analytics, shared projects, rule assurance
  and implementation mappings. TypeScript and production Vite build passed.
- Regression: five DashboardStatsService tests and two modular architecture
  tests passed. These checks are not a full application integration suite.
- Browser: dashboard trend values, run 5 lifecycle rows, and all three service
  hotspot scores were checked against the live Neon-backed preview. Payment's
  expanded explanation showed F=1, average severity=2.625 and P=0.8.
- Cleanup: the disposable PostgreSQL test container was already absent when
  final cleanup was attempted. The local read-only preview remains available.
- Neon: private migration succeeded; five demo runs and 15 occurrences imported
  and verified across three services. Expected scores: Payment
  0.8729166666666667, Order 0.4166666666666667, Notification
  0.0333333333333333. Run 5 versus run 4 has 2 new, 0 resolved, 3 persisting.
- One Neon verification at `2026-10-19T21:42:38.491154400Z` measured
  7075.314 ms for connection/read/calculation over that small fixture.
  This is a single cold-path observation, not a production performance benchmark.

The read implementation currently loads a project's history into memory.
Large-history performance, real authenticated access and live upstream integration
remain unverified and are required before production rollout.

## AI assistance

Codex generated the ingestion continuation, comparison/ranking implementation,
query APIs, frontend, tests and these notes, then ran the checks recorded above.
No independent architect review or teammate approval of the upstream contract
is claimed.
