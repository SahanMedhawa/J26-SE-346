# Persistent structural validation and architect review (PP1)

This extends Component 1 only. It does not implement READY, final assurance gates, C2 export,
fidelity, conflict detection, or frontend screens. A supported signature is a recognized structural
shape, including when the rule decision is FORBIDDEN. Human confirmation does not change the
automated structural result or imply architectural permission.

## Persistence and migration

`V6__structural_validation_and_architect_reviews.sql` is discovered by the existing rules Flyway
configuration during application startup. V1–V5 are unchanged; no manual Neon alterations are
required. V6 creates three tables and change-tracking triggers:

* `rule_structural_validations`: one current result per rule, with a UUID, SUPPORTED/UNSUPPORTED
  result, explanation, matched signature ID, selected architecture style, fingerprint, revision,
  and validation timestamp. Revalidation upserts this row and preserves its UUID. Missing rows
  mean NOT_VALIDATED; migration does not create successful results.
* `rule_evidence_revisions`: per-rule structural and review revision counters. Missing rows mean
  zero. These counters make invalidation durable even when an edit is later reverted.
* `architect_reviews`: append-only decisions, supplied reviewer identifiers, comments, decision
  timestamps, ADR UUID/version, structural status, and fingerprints for rule, ADR, mapping,
  and combined evidence. PostgreSQL identity ordering gives deterministic newest-first history.

The current-result table and revision table use rule UUID foreign keys with cascade deletion.
The review table retains a rule UUID and project UUID snapshot: its nullable live rule foreign key
is set to null when a rule is deleted, preserving audit decisions without breaking rule DELETE.
There are no review update/delete endpoints. Deleted rules return 404 through the scoped APIs;
their retained audit snapshots are available only in the database, not a new audit API.

The catalogue currently defines no version. `catalogueVersion` is null; no artificial version is
assigned. The fingerprint includes the matching catalogue definitions to detect catalogue changes.

## Currentness and locking

Structural fingerprints use SHA-256 with length-prefixed UTF-8 values: registered architecture
styles, selected validation style, source element type, relationship type, target element type,
and catalogue matching definitions. Rule decisions, element names, descriptions, severity,
mapping paths, and ADR content do not determine structural signature recognition.

V6 tracks updates through the existing APIs and JDBC tables:

| Change | Structural validation | Architect review |
| --- | --- | --- |
| Rule source/target type or relationship | NOT_VALIDATED until revalidated | PENDING |
| Project architecture styles | NOT_VALIDATED until revalidated | PENDING |
| Rule source/target names, decision, description, severity, ADR link | Preserved | PENDING |
| Linked ADR content/status/version | Preserved | PENDING |
| Implementation mapping selections | Preserved | PENDING |
| Project repository URL | Preserved | PENDING |
| Project name/description | Preserved | Preserved |
| No-op rule/ADR/mapping updates | Preserved | Preserved |

Stale validation evidence is retained, but its effective GET status is NOT_VALIDATED and
`current` is false. The GET includes the last validation timestamp, status and revision for
explanation. Revalidation of identical inputs does not invalidate a current review merely because
its validation timestamp changed.

Review fingerprints include rule interpretation, linked ADR content/version, mapping sides/paths,
repository identity, structural evidence, and the review revision. Mapping UUIDs, mapping input
order, and unrelated update timestamps are excluded. A stale latest decision yields effective
PENDING; it is never deleted or replaced. History `current` means that historical record still
matches today's evidence; only the newest matching latest decision determines effective status.

Validation and review operations use the named rules transaction manager. They lock the selected
project FOR SHARE, the project-scoped rule FOR UPDATE, then its ADR FOR SHARE before reading
evidence. Existing rule and ADR updates conflict with these locks; existing mapping replacement
already locks its parent rule. Revisions and results are read/written in that transaction. Deadlock
or concurrency failures return 409; general database failures return a safe 503 error.

To prevent a stale form from confirming evidence that changed before the request acquired its
locks, CONFIRMED submissions **require** `expectedEvidenceFingerprint` copied from the current
review workspace GET. A mismatch returns 409 without adding a history record. Change requests
may supply this guard too, but are not required to do so.

## Confirmation policy and prototype identity

CONFIRMED requires a current SUPPORTED structural result, an existing linked ADR whose UUID
and current version are available, at least one SOURCE and one TARGET implementation mapping,
a nonblank reviewer identifier of at most 150 characters, and a matching evidence fingerprint.
This PP1 mapping policy is applied to every confirmation; missing mappings are not complete.
No file contents are inspected and no architectural implementation claim is inferred from paths.

CHANGES_REQUESTED is allowed for supported, unsupported, unvalidated, or incomplete rules.
It requires reviewer identity and comments of at least 10 non-padding characters (maximum 4000).
CONFIRMED comments are optional. PENDING is an effective default, not a submitted decision.

There is no implemented authentication mechanism for reviewers. The reviewer is explicitly
caller-supplied, and returned history includes `reviewerAuthenticated: false`. This does not
authenticate an architect, grant a role, or establish a production approval identity.

## REST API and examples

Use these Postman variables: `baseUrl`, `projectId`, `ruleUuid`, and `evidenceFingerprint`.
`projectId`, `ruleUuid`, and ADR UUIDs are internal UUIDs, not readable IDs such as R-001.

### Run existing structural validation

```http
POST {{baseUrl}}/api/rules/projects/{{projectId}}/rules/{{ruleUuid}}/validate-structure
Content-Type: application/json
```

```json
{"architectureStyle":"MICROSERVICES"}
```

Select the actual registered style. A body is optional for projects with a single applicable
style; the existing multi-style selection requirement remains unchanged. The original response
contract is preserved, for example:

```json
{
  "ruleId": "R-001",
  "ruleUuid": "<rule UUID>",
  "projectId": "<project UUID>",
  "architectureStyle": "MICROSERVICES",
  "sourceElementType": "SERVICE",
  "relationshipType": "DIRECT_DATABASE_ACCESS",
  "targetElementType": "DATABASE",
  "decision": "FORBIDDEN",
  "validationStatus": "SUPPORTED",
  "matchedSignatureId": "M08",
  "message": "Structural signature is recognized by ARVM."
}
```

UNSUPPORTED also returns 200 and is persisted. Invalid saved data, missing records, or invalid
style selections retain the existing validation errors instead of manufacturing a result.

### Retrieve stored structural evidence

```http
GET {{baseUrl}}/api/rules/projects/{{projectId}}/rules/{{ruleUuid}}/structural-validation
```

```json
{
  "ruleUuid": "<rule UUID>",
  "status": "SUPPORTED",
  "reason": "Structural signature is recognized by ARVM.",
  "matchedSignatureId": "M08",
  "architectureStyle": "MICROSERVICES",
  "catalogueVersion": null,
  "validatedFingerprint": "<64-character SHA-256>",
  "validatedAt": "2026-10-18T10:30:00Z",
  "current": true,
  "lastValidationStatus": "SUPPORTED",
  "validatedRevision": 0,
  "currentRevision": 0
}
```

Never-validated rules return 200 with NOT_VALIDATED, current=false, an explanatory reason, and
null prior evidence. Stale rows return NOT_VALIDATED while retaining their last evidence fields.

### Read architect workspace

```http
GET {{baseUrl}}/api/rules/projects/{{projectId}}/rules/{{ruleUuid}}/architect-review
```

Example before the first decision, after validation and mappings are complete:

```json
{
  "ruleUuid": "<rule UUID>",
  "ruleId": "R-001",
  "adrUuid": "<linked ADR UUID>",
  "adrDisplayId": "ADR-001",
  "adrVersion": 1,
  "structuralValidationStatus": "SUPPORTED",
  "structuralValidationCurrent": true,
  "reviewStatus": "PENDING",
  "confirmationEligible": true,
  "blockedReasons": [],
  "evidenceFingerprint": "<64-character SHA-256>",
  "evidenceRevision": 2,
  "sourceFileCount": 1,
  "targetFileCount": 1,
  "latestReview": null
}
```

Copy the actual fingerprint, or use this Postman post-response script on the workspace GET:

```javascript
pm.environment.set("evidenceFingerprint", pm.response.json().evidenceFingerprint);
```

### Submit decision

```http
POST {{baseUrl}}/api/rules/projects/{{projectId}}/rules/{{ruleUuid}}/architect-review
Content-Type: application/json
```

```json
{
  "decision": "CONFIRMED",
  "reviewer": "Software Architect",
  "comments": "The interpretation and linked evidence have been reviewed.",
  "expectedEvidenceFingerprint": "{{evidenceFingerprint}}"
}
```

HTTP 200 returns the workspace response with reviewStatus=CONFIRMED and latestReview containing
the appended review record, current=true, and reviewerAuthenticated=false. It does not return or
set READY. Invalid confirmation eligibility or a stale fingerprint returns 409; a missing required
fingerprint, invalid decision/reviewer, or missing change-request explanation returns 400.

```json
{
  "decision": "CHANGES_REQUESTED",
  "reviewer": "Software Architect",
  "comments": "Please clarify the target element and complete its implementation mappings."
}
```

### Retrieve history

```http
GET {{baseUrl}}/api/rules/projects/{{projectId}}/rules/{{ruleUuid}}/architect-review/history
```

Returns a newest-first array. Each entry has `review` (UUIDs, decision, reviewer, comments,
decidedAt, reviewed ADR version, structural status and evidence fingerprints), `current`, and
`reviewerAuthenticated: false`. Unknown projects/rules and cross-project rule access return 404
for all operations. Consistent errors use the existing ApiError response shape.

## Manual HealthMate R-001 demonstration

1. Start the existing backend normally; Flyway applies V6. Do not run manual ALTER statements.
2. GET `/api/rules/projects`, find HealthMate, and copy its `id` into `projectId`.
3. GET `/api/rules/projects/{{projectId}}/rules`, locate readable `ruleId: R-001`, and copy its
   internal `id` into `ruleUuid`. Keep the rule's actual structural definition and ADR link.
4. GET the new structural-validation and architect-review endpoints. An unvalidated rule starts
   NOT_VALIDATED/PENDING; the workspace explains any missing evidence.
5. Run the existing validate-structure POST using the appropriate registered architecture style.
   If UNSUPPORTED, demonstrate CHANGES_REQUESTED; never override it to force confirmation.
6. Use the existing repository browsing/mapping APIs to provide at least one SOURCE and TARGET
   path for R-001. Mappings remain managed separately and use their unchanged contracts.
7. Review the linked ADR/version, GET the workspace, copy its evidenceFingerprint, then POST
   CONFIRMED using that fingerprint. GET workspace/history to see the decision and timestamp.
8. To test staleness on a demonstration rule, change a mapping, ADR, or rule interpretation using
   its existing API. GET workspace: it is PENDING and the historical confirmation remains. A
   structural edit also makes structural validation NOT_VALIDATED until rerun.
9. Repeating unchanged PUT payloads does not invalidate confirmation. Revalidating unchanged
   structural inputs does not invalidate it either. Changing and then reverting evidence does
   not revive an old confirmation; a new review is required.

These instructions do not assert that the existing HealthMate rule is supported, mapped, or
confirmed. Those facts depend on its actual saved data; no live HealthMate/Neon records were
changed during implementation.

## Automated verification

From the repository root with JDK 25:

```text
mvn -pl akegp-rules -am test
mvn -pl akegp-rules -am package
```

Unit/controller tests run without network access. The existing GitHub tests continue using
mocked responses. PostgreSQL tests require `AKEGP_TEST_POSTGRES_URL`, with
`AKEGP_TEST_POSTGRES_USER` and `AKEGP_TEST_POSTGRES_PASSWORD` as needed. They migrate isolated
UUID-named schemas, validate Flyway compatibility, test trigger invalidation/no-op behavior,
transaction rollback, audit retention, and concurrent mapping/ADR/rule/project edits. Without a
test database, these tests are skipped; migration execution and row-lock behavior then remain
unverified against PostgreSQL. Production Neon is not used as a substitute test database.
