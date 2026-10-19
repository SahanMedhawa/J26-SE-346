-- Block legacy writers while validating and taking the initial high-water marks.
LOCK TABLE adrs, architecture_rules IN SHARE ROW EXCLUSIVE MODE;

-- Preserve legacy values verbatim. Fail with an actionable error instead of
-- silently ignoring malformed IDs or repairing a database with missing constraints.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM adrs WHERE adr_id !~ '^ADR-[0-9]{3,}$')
       OR EXISTS (SELECT 1 FROM architecture_rules WHERE rule_id !~ '^R-[0-9]{3,}$') THEN
        RAISE EXCEPTION 'Malformed legacy readable IDs found. Audit adrs.adr_id and architecture_rules.rule_id before retrying V4; no records have been changed.';
    END IF;
    IF EXISTS (SELECT 1 FROM adrs GROUP BY project_id, adr_id HAVING count(*) > 1)
       OR EXISTS (SELECT 1 FROM architecture_rules GROUP BY project_id, rule_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Duplicate legacy readable IDs found within a project. Resolve explicitly before retrying V4; no records have been changed.';
    END IF;
END $$;

CREATE TABLE project_record_counters (
    project_id UUID NOT NULL REFERENCES registered_projects (id),
    record_type VARCHAR(4) NOT NULL CHECK (record_type IN ('ADR', 'RULE')),
    -- 64-character IDs permit up to 62 numeric digits for rules.
    last_number NUMERIC(62, 0) NOT NULL CHECK (last_number >= 0),
    PRIMARY KEY (project_id, record_type)
);

INSERT INTO project_record_counters (project_id, record_type, last_number)
SELECT project_id, 'ADR', max(substring(adr_id FROM 5)::numeric)
FROM adrs GROUP BY project_id
UNION ALL
SELECT project_id, 'RULE', max(substring(rule_id FROM 3)::numeric)
FROM architecture_rules GROUP BY project_id;

-- No audit/version history exists in V1-V3. IDs deleted before V4 cannot be
-- recovered. After V4 counters survive record deletion and must never be reset.
