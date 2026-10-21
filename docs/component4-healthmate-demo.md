# HealthMate analytics demonstration

No architecture health score is calculated or displayed.

## Data provenance

The running platform identified HealthMate as project `1b7b6197-7fe4-4bf3-afe4-7e500d564b81`, repository `https://github.com/SahanMedhawa/HealthMate.git`.
Read-only inspection found five stored rules, of which four were exported with `SUPPORTED` validation and `CONFIRMED` architect review:

- R-001 / ADR-001: AppointmentService must not directly access PatientDB (HIGH).
- R-002 / ADR-001: AppointmentService is required to call PatientService (MEDIUM).
- R-003 / ADR-002: AppointmentService must not directly access DoctorDB (HIGH).
- R-004 / ADR-002: AdminService must not directly access DoctorDB (HIGH).

R-005 was not eligible for export and is not used by the generator. The generator always reads the current export rather than hardcoding these rule IDs. It includes every exported source mapping, both target mapping sides, rule relationship, decision, severity and ADR reference. A SHA-256 metadata fingerprint identifies each generated dataset. Changes create a separate version instead of overwriting immutable history.

Two recorded HealthMate graph scans and two documentation-only pipeline records were also found. The pipeline records carried the old `project-A` identifier. They are displayed as repository-matched context with a mapping warning; they are never converted into complete conformance snapshots. New pipeline events now resolve registered repositories to their actual project UUID, with the configured resolver retained for unregistered demo repositories. Old records are not rewritten.

**Synthetic:** 96 historical runs, dates, commit labels, violation observations, code-site suffixes, LOC and gate outcomes. Real mappings establish plausible locations, not proof of a detected defect. The synthetic timeline predates project registration; it is an experimental scenario using current metadata, not repository history replay. The reference five-run fixture remains unchanged for regression tests.

## Reproduce

Start the normal application and web services. Analytics schema migration remains explicit, using the existing `akegp-analytics/scripts/neon.ps1 -Operation migrate -ConfirmNeonAnalytics` command when the schema does not exist. Startup does not seed or migrate analytics.

For local presentation use, both `AKEGP_ANALYTICS_DEVELOPMENT_ACCESS=true` and `AKEGP_ANALYTICS_DEMO_ENABLED=true` must be enabled (the existing Compose defaults). Open Projects → HealthMate → Architecture trends and choose **Create project demo history**. Imports are replay-safe and may be retried after interruption. The DEMO importer writes all new runs in one bulk transaction and safely replays any unchanged prefix left by an older import. The ordinary recorded ingestion port retains per-run transactions.

Equivalent commands from the repository root:

```powershell
./akegp-analytics/scripts/project-history.ps1 -ProjectId 1b7b6197-7fe4-4bf3-afe4-7e500d564b81 -Operation import
./akegp-analytics/scripts/project-history.ps1 -ProjectId 1b7b6197-7fe4-4bf3-afe4-7e500d564b81 -Operation verify
./akegp-analytics/scripts/project-history.ps1 -ProjectId 1b7b6197-7fe4-4bf3-afe4-7e500d564b81 -Operation manifest -OutputFile ./healthmate-demo-manifest.json
```

The manifest includes the current exported rules and the reproducible snapshots; it contains no credentials. Import writes only `analytics_private`. Rule, conformance and enforcement data remain owned by their modules.

## Demonstration sequence

1. Open `/projects`, choose HealthMate, and show its four component cards. Each module now has a project-scoped route and preserves context on refresh.
2. Open Architecture trends. Choose the project demo dataset and `refs/heads/main` (84 runs); the separate `refs/heads/demo/appointment-refactor` branch contains 12 runs.
3. Show active count, severity, lifecycle and density trends. Select a point or heatmap cell to inspect its run. Expand chart data for accessible numeric observations.
4. Compare main runs 23–25: run 24 failed, so run 25 establishes a new baseline rather than reporting invented fixes. Run 48 is partial, and run 49 also restarts comparison.
5. Select run 57 to show the synthetic rule-context version boundary. Counts remain observable, but lifecycle comparison does not bridge the boundary.
6. Runs 63 and 64 are complete zero-violation snapshots. Run 65 introduces returning identities; they are NEW relative to run 64, with earlier occurrences still visible in historical lifecycles.
7. Open Lifecycle, filter by service/rule/severity/evidence, and expand an identity's timeline. First/last observation and recurrence describe complete observations in the selected date range. Current streak ends at the selected run and cannot bridge incomplete/version boundaries. Absence during incomplete analysis is UNKNOWN.
8. Open Hotspots. Compare Services, Modules (source directories), and Rules. Module grouping projects each existing finding onto its source directory; it creates no extra findings. Compare the latest five runs (AdminService leads) with twenty runs (AppointmentService leads). Charts, recurrence and persistence use the same selected comparable observations.
9. Open Run & governance history to show actual rule evidence, real graph scans and pipeline decisions separately from synthetic architecture snapshots.
10. Paste the HealthMate GitHub URL into Repository history. URL normalization accepts `.git`, trailing slash and case differences, and opens the same registered project's history. Unknown public repositories show a GitHub activity preview and offer registration. The project history also shows this preview: repository creation/last-push dates, default branch, language and up to 20 recent commits. These are public GitHub observations, not historical architecture results; rate limits and unavailable/private repositories are shown explicitly.

## Metrics and contracts

Finding filters apply before comparisons, chart aggregation, lifecycle pagination and hotspot cohort normalization. Date bounds are UTC `[from,to)`, while earlier observations remain available internally to establish the predecessor for the first visible point. Density is violations per 1,000 analyzed lines; it is unavailable when finding filters do not have a matching scoped LOC denominator. Demo LOC is synthetic. Null metrics are rendered as unavailable and produce gaps rather than zeroes in charts.

Hotspots retain the existing frequency/severity/persistence model with weights 2/6, 3/6 and 1/6. Scores prioritize a cohort; they are not architecture health scores. Inactive historical entities remain in the ranking where supported by the selected comparable window. Recurrence remains a separate diagnostic.

Project-scoped additions:

- `GET /api/analytics/repository?url=...`: resolve an existing registered project, checking its analytics read authorization.
- `GET /api/analytics/repository/activity?url=...`: bounded public GitHub activity preview. Only the fixed GitHub API host is contacted, responses are capped at 1 MiB, redirects are disabled and no credentials are sent. Two read requests are used per preview; GitHub rate limits can make this optional panel unavailable without affecting stored analytics.
- `GET /api/analytics/projects/{projectId}/context`: join project/rule exports, graph scan metadata and repository-matched pipeline context through module interfaces. Partial context failures are surfaced as warnings.
- `POST .../demo-history`: explicitly enabled development import, with server-assigned DEMO provenance.
- `GET .../demo-manifest`: current rule export plus deterministic synthetic snapshots.
- `GET .../explorer?currentRunId=...`: complete-history aggregates and paginated identity timelines, with service/rule/severity/search/from/to filters.
- Existing `/comparisons`, `/violations`, `/dashboard` and `/hotspots` now also accept finding filters. The selected run's stream isolates branches, datasets and upstream sources.
- `POST .../snapshots`: opt-in recorded full-snapshot ingress using `x-analytics-key` matched against backend `AKEGP_ANALYTICS_INGESTION_KEY`. Empty configuration disables it. The server assigns UPSTREAM provenance. Caller project/repository must match; findings must use currently exported source entities, mapped source/target file anchors (`path#stableSymbol`), relationships and numeric severities HIGH=3, MEDIUM=2, LOW=1. Stable upstream IDs remain disabled until their guarantees are verified.

The ingress body is the shared `AnalysisSnapshot` v1 contract. Publishers must supply full scope, branch, run sequence, timestamps, commit, rule/detector/mapping/scope versions, explicit completeness, and full findings. They must keep structural symbol anchors stable across line movement. Graph-only extraction, mock detector output and documentation-only pipeline passes must not be published as complete real architecture snapshots. Component 2 still needs a real detector/publisher before automatic recorded architecture history is available.

Development project access retains the existing local policy; it is not production user membership authentication. A production host must provide the membership authorizer and a suitable publisher credential policy.

## Evaluation boundaries

Verification on 2026-10-20: the full backend reactor passed. Component 4 ran 38 tests with no skips, including seven PostgreSQL integration tests. All 68 frontend tests and the production build passed. The revised v2 dataset imported 96 runs (84 main, 12 refactor) using four exported rules. Main has 82 complete and two incomplete analyses. Tests verify the latest comparison: 16 active = 3 new + 13 persistent; previous 14 = 1 resolved + 13 persistent; net +2 = 3 new - 1 resolved. The reproducible JSON is saved as `artifacts/analytics/healthmate-demo-manifest.json`.

Analytics and Rules now reuse `ProjectWorkspaceLayout`, including the common platform header. Analysis mode explicitly selects either a run count or UTC date bounds, and Dashboard/Lifecycle share the baseline selector. Individual trend points compare adjacent runs; the summary cards compare the selected current and baseline runs. The optional Explorer `runs` parameter aligns its observation window and streaks with Hotspots; older calls without it retain full-history behavior. Immutable v1 datasets remain available as older history versions. The v2 scenarios replace repeating patterns with durable, resolved, returning and emerging findings; all statistics derive from those snapshots. Source provenance remains available in the collapsed Data sources panel.

Browser verification: the five-run service ranking showed AdminService 0.947 ahead of AppointmentService 0.551; the twenty-run ranking reversed the order (AppointmentService 0.861, AdminService 0.529). The October 15–20 UTC range reproduced the five-run observations and scores. Module grouping populated eight directories; Rule grouping populated four rules. Navigation preserved the range. Choosing run 80 as the baseline changed the run 84 summary to 16 active, 11 new, 5 persistent, 2 resolved and net +9. Cloud database connections were intermittent during deployment, including an existing rules-database startup failure; a backend restart recovered it. The v2 import and verification subsequently succeeded.

Deterministic scenarios establish known identities, new/resolved/persisting transitions, fixes, recurrence and completeness boundaries. Unit/API tests check these calculations and project/source isolation. PostgreSQL integration tests exercise immutable replay, batch writes, rollback on identity conflict, ordering, concurrency and the complete 96-run import, including replay of an interrupted prefix and atomic rollback of a conflicting bulk dataset. These demonstrate functional correctness on synthetic scenarios, not empirical accuracy of an architecture detector or validation of a health model. Historical commit replay and cross-project comparative evaluation remain future work.

## Loading performance follow-up

The loading effect is split by endpoint. Comparison state, transition pagination and baseline changes refresh Comparisons only; ranking dimension/pagination refresh Hotspots only; lifecycle pagination refreshes Explorer only. Shared finding filters refresh the affected endpoints concurrently, without waiting for Hotspots to establish a run already identified by stored run metadata. The date-window anchor remains isolated to the selected stream. Existing results stay visible with an updating notice, and cancelled requests cannot replace newer selections.

The backend retains immutable project history in a bounded cache of approximately 32 projects. Every read verifies the project's stored run IDs through a lightweight query, so imports/new snapshots invalidate the entry immediately on the next request. Authorization still executes for each API call. Failed revision checks surface storage errors rather than returning cached history. Full reads no longer select unused canonical snapshot payloads. No schema or endpoint changes were needed.

Measured on 2026-10-20 against the same HealthMate project: before optimization, Hotspots/Comparisons/Explorer took 5.21/5.30/4.83 seconds. After deployment with history warmed, they took 1.55/1.28/2.01 seconds; the run list took 1.35 seconds. Initial history loading after restart took 8.70 seconds. These are individual observations, not latency guarantees; cloud connections can still fail or take longer. Browser checks confirmed State and Severity updates preserve both lifecycle sections and show an updating notice instead of whole-page loading. Totals remained 16 active, 3 new, 13 persistent, 1 resolved, net +2 (High-only: 15 active, 3 new, 12 persistent, 1 resolved, net +2).

Validation: all 71 frontend tests and the production build passed, and the full backend reactor passed with all 40 Analytics tests (including eight local PostgreSQL integration tests). New tests cover request independence, removal of the Hotspots waterfall, retained visible content, project cache isolation, new-run invalidation and failure handling.
