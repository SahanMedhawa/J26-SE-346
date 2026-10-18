# Component 1 rule export contract (PP1)

`GET /api/rules/projects/{projectId}/export`

`projectId` is the registered project's internal UUID. No body is required. The endpoint uses
existing PostgreSQL data and does not contact GitHub, validate rules, submit reviews, or modify
records. It returns a consistent snapshot in a read-only REPEATABLE READ transaction.

Example shape (illustrative values; production values always come from persisted records):

```json
{
  "projectId": "76ec33be-9c07-49c3-b5ef-b59cf1654b45",
  "projectName": "HealthMate",
  "repositoryUrl": "https://github.com/owner/HealthMate.git",
  "architectureStyle": "MICROSERVICES",
  "ruleCount": 1,
  "rules": [
    {
      "ruleId": "R-001",
      "adrId": "ADR-001",
      "sourceElement": "AppointmentService",
      "sourceElementType": "SERVICE",
      "relationshipType": "DIRECT_DATABASE_ACCESS",
      "targetElement": "PatientDB",
      "targetElementType": "DATABASE",
      "decision": "FORBIDDEN",
      "severity": "HIGH",
      "validationStatus": "SUPPORTED",
      "architectReviewStatus": "CONFIRMED",
      "sourceFiles": ["src/main/java/example/AppointmentService.java"],
      "targetFiles": ["src/main/resources/db/patient.sql"]
    }
  ]
}
```

| Field | Meaning |
| --- | --- |
| `projectId`, `projectName`, `repositoryUrl` | Selected project's stored identity and repository metadata; URL is unchanged |
| `architectureStyle` | Stored single style, or `MICROSERVICES_AND_LAYERED` for a combined/multi-style project; null for a legacy project without styles |
| `ruleCount` | Exactly the number of entries in `rules` after filtering |
| `ruleId`, `adrId` | Human-readable IDs from the rule and its linked ADR, not database UUIDs |
| Elements, types, relationship, decision, severity | Existing saved rule values; SUPPORTED does not imply ALLOWED |
| `validationStatus` | Current saved structural status; every included rule is SUPPORTED |
| `architectReviewStatus` | Effective human review status; every included rule is CONFIRMED |
| `sourceFiles`, `targetFiles` | Exact saved repository-relative strings, retaining each side's existing mapping repository order |

The existing JDBC mapping query orders by side and file path. Export does not normalize,
re-sort, deduplicate, or rewrite its output. Original selection/insertion order is not stored in
the current mapping schema. The rule list retains the existing project rule repository order.

## Filtering and errors

A rule is included only if it belongs to this ACTIVE project, its linked ADR exists and is
PROPOSED or ACCEPTED, and the existing review workspace calculation reports both confirmation eligibility
and effective CONFIRMED status. That shared calculation verifies current SUPPORTED structural
validation, current review/ADR/mapping fingerprints and revisions, and at least one file on both
sides. There is no second structural validator or parallel currentness calculation.

For this PP1 contract, a PROPOSED ADR does not block a currently confirmed rule. Export reuses
the effective Architect Confirmation workflow rather than imposing an ACCEPTED-only gate.
DEPRECATED and SUPERSEDED ADRs remain excluded even if an architect reviewed their current contents. Inactive projects
return no rules. Missing/deleted rules and ADRs, pending/change-requested reviews, missing
mappings, stale validation, and stale confirmations are excluded. Changes must be revalidated
and/or reconfirmed through the existing APIs as appropriate.

* HTTP 200: existing project, including `ruleCount: 0` and `rules: []` when no rules qualify.
* HTTP 404: unknown project.
* HTTP 400: malformed project UUID.
* Database failures use the existing Component 1 safe API error handling.

Concurrent commits cannot mix metadata, rule structure, mappings, or confirmation evidence from
different snapshots. The response reflects the database snapshot at request execution, not a
promise that evidence cannot change after it is returned. Re-fetch before a later C2 run.

## Component 2 consumption

Fetch this endpoint for the selected project and deserialize the wrapper plus `rules` array.
Scope readable rule/ADR IDs by `projectId`; the same readable IDs can exist in other projects.
Use the rule's saved types, relationship and decision for deterministic structural checking,
and its source/target paths for locating implementation inputs in the named repository.
Handle combined architecture styles explicitly rather than assuming every project is
MICROSERVICES. An empty array means no currently eligible rules, not a missing project.

This is a PP1 integration export of supported, architect-confirmed rules. It does **not** assert
the complete final five-gate READY assurance process, fidelity, conflict freedom, or that file
existence proves an architectural implementation. No Component 2 implementation is changed.

## Local testing

1. Start the existing backend with its normal database configuration and migrations V1–V6.
2. GET `/api/rules/projects`, find the desired project, and copy its UUID. Do not use its name
   or a readable rule ID as `projectId`.
3. For an eligible rule, ensure its ADR exists and is PROPOSED or ACCEPTED, validate its structure, save both mapping
   sides, then GET its architect-review workspace and POST CONFIRMED with that evidence
   fingerprint. An ADR status/version change requires a fresh architect review.
4. In Postman, GET `{{baseUrl}}/api/rules/projects/{{projectId}}/export`, or run:

```powershell
Invoke-RestMethod 'http://localhost:8080/api/rules/projects/<actual-project-uuid>/export' |
    ConvertTo-Json -Depth 10
```

5. Modify a confirmed rule or mapping through its existing API and fetch again: that rule is
   excluded until its evidence is eligible and confirmed again. No records are changed by GET.

Tests: `mvn -pl akegp-rules -am test`; package: `mvn -pl akegp-rules -am package` (JDK 25).
PostgreSQL tests run only when `AKEGP_TEST_POSTGRES_URL` is configured, with optional
`AKEGP_TEST_POSTGRES_USER` and `AKEGP_TEST_POSTGRES_PASSWORD`. They use isolated random schemas;
they do not use live Neon as an implicit test database.
