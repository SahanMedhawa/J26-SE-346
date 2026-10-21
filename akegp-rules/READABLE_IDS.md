# Generated ADR and Rule identifiers

Create ADRs without `adrId`; create rules without `ruleId`. These fields are
retained in create DTOs only to reject supplied values with HTTP 400 and a field
error. Omission or JSON null is accepted. Rule requests must still supply `adrId`:
that field is the linked ADR's UUID, not its readable identifier.

Routes and response shapes remain unchanged. Responses include the internal UUID
and the generated readable ID. Update requests may omit the readable ID or send
its exact existing value; changing it returns HTTP 400. UUIDs, project ownership,
ADR versioning, and rule linking retain their existing behavior.

## Persistence and rollout

V4 adds `project_record_counters`, keyed by project UUID and record type. An atomic
PostgreSQL upsert increments the counter and returns its value. Row locks serialize
allocations for the same key. ADR and Rule services use `rulesTransactionManager`
for both allocation and insertion. The allocator requires an existing transaction.
Rollback undoes both operations; a failed, uncommitted creation does not reserve an
ID. Committed IDs are never reused after deletion. Counters persist across restarts.

Formatting pads to at least three digits and never truncates at 999. Numeric
counters accommodate the existing 64-character readable-ID columns. Existing
project/readable-ID unique constraints remain in force.

V4 locks the record tables while validating and seeding counters from the highest
numeric suffix per project/type. It preserves all existing identifiers, UUIDs,
versions, timestamps, and relationships. Leading-zero variants are retained and
contribute their numeric values to the maximum. Gaps are not filled.

Malformed legacy IDs or duplicate strings within a project/type abort migration
with an explicit error; the PostgreSQL migration transaction rolls back. Audit the
reported tables and resolve anomalies explicitly before retrying. No automatic
renaming, deletion, or deduplication occurs. The repository schema contains no
history of deleted records or prior identifier edits, so historical high-water
marks that exceed all surviving IDs cannot be recovered. If external audit data
exists, raise the seeded counter explicitly before accepting new writes.

Drain old backend writers during rollout: legacy instances must not continue
accepting manually assigned IDs after V4. Never delete or reset counters when
deleting ADRs or rules. Frontend creation forms must stop sending the generated
fields; frontend code is unchanged by this backend task.

## Verification

Run with Java 25:

```text
mvn -pl akegp-rules -am test
```

`ReadableIdPostgresTest` runs only when `AKEGP_TEST_POSTGRES_URL` is set to a
PostgreSQL JDBC URL. Optional credentials are `AKEGP_TEST_POSTGRES_USER` and
`AKEGP_TEST_POSTGRES_PASSWORD`. Use a disposable test database whose user can
create schemas. Each test migrates a random schema and drops only that schema
afterward; existing application schemas are not migrated.

The PostgreSQL suite checks legacy V3-to-V4 migration, malformed and duplicate
legacy IDs, leading-zero suffixes, numbering past 999, independent projects,
persisted counters after deletion, transaction rollback, and concurrent service
creations with separate database connections. Tests without this URL explicitly
skip PostgreSQL coverage; service doubles do not verify database concurrency.
