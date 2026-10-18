-- Existing rules start with implicit revision zero and no validation/review decisions.
CREATE TABLE rule_evidence_revisions (
    rule_id UUID PRIMARY KEY REFERENCES architecture_rules (id) ON DELETE CASCADE,
    structural_revision BIGINT NOT NULL DEFAULT 0 CHECK (structural_revision >= 0),
    review_revision BIGINT NOT NULL DEFAULT 0 CHECK (review_revision >= 0)
);

CREATE TABLE rule_structural_validations (
    id UUID PRIMARY KEY,
    rule_id UUID NOT NULL UNIQUE REFERENCES architecture_rules (id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('SUPPORTED', 'UNSUPPORTED')),
    reason TEXT NOT NULL,
    matched_signature_id VARCHAR(64),
    architecture_style VARCHAR(32) NOT NULL CHECK (architecture_style IN ('MICROSERVICES', 'LAYERED')),
    catalogue_version VARCHAR(100),
    structural_fingerprint CHAR(64) NOT NULL,
    structural_revision BIGINT NOT NULL CHECK (structural_revision >= 0),
    validated_at TIMESTAMPTZ NOT NULL,
    CHECK ((status = 'SUPPORTED' AND matched_signature_id IS NOT NULL)
        OR (status = 'UNSUPPORTED' AND matched_signature_id IS NULL))
);

CREATE TABLE architect_reviews (
    id UUID PRIMARY KEY,
    review_order BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    -- Keep audit evidence if a rule is deleted, without blocking existing rule DELETE behavior.
    rule_id UUID REFERENCES architecture_rules (id) ON DELETE SET NULL,
    rule_uuid UUID NOT NULL,
    project_uuid UUID NOT NULL,
    decision VARCHAR(24) NOT NULL CHECK (decision IN ('CONFIRMED', 'CHANGES_REQUESTED')),
    reviewer VARCHAR(150) NOT NULL CHECK (btrim(reviewer) <> ''),
    comments VARCHAR(4000),
    decided_at TIMESTAMPTZ NOT NULL,
    rule_fingerprint CHAR(64) NOT NULL,
    adr_uuid UUID,
    adr_version INTEGER CHECK (adr_version >= 1),
    adr_fingerprint CHAR(64),
    mapping_fingerprint CHAR(64) NOT NULL,
    evidence_fingerprint CHAR(64) NOT NULL,
    review_revision BIGINT NOT NULL CHECK (review_revision >= 0),
    validation_status VARCHAR(16) NOT NULL CHECK (validation_status IN ('NOT_VALIDATED', 'SUPPORTED', 'UNSUPPORTED')),
    validation_fingerprint CHAR(64),
    CHECK (rule_id IS NULL OR rule_id = rule_uuid),
    CHECK (decision <> 'CONFIRMED' OR (validation_status = 'SUPPORTED' AND adr_uuid IS NOT NULL
        AND adr_version IS NOT NULL AND validation_fingerprint IS NOT NULL)),
    CHECK (decision <> 'CHANGES_REQUESTED' OR (comments IS NOT NULL AND length(btrim(comments)) >= 10))
);
CREATE INDEX architect_reviews_rule_history_idx ON architect_reviews (rule_uuid, review_order DESC);

-- Database-side revisions cover the existing CRUD paths without rewriting their semantics.
-- They also prevent a change-then-revert from reviving old evidence.
CREATE FUNCTION advance_rule_evidence(p_rule UUID, p_structural BOOLEAN) RETURNS VOID
LANGUAGE plpgsql AS $$
BEGIN
    -- Parent rule deletions cascade through mappings; do not recreate state for a deleted rule.
    IF EXISTS (SELECT 1 FROM architecture_rules WHERE id = p_rule) THEN
        INSERT INTO rule_evidence_revisions (rule_id, structural_revision, review_revision)
        VALUES (p_rule, CASE WHEN p_structural THEN 1 ELSE 0 END, 1)
        ON CONFLICT (rule_id) DO UPDATE SET
            structural_revision = rule_evidence_revisions.structural_revision + EXCLUDED.structural_revision,
            review_revision = rule_evidence_revisions.review_revision + 1;
    END IF;
END;
$$;

CREATE FUNCTION track_rule_evidence() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(OLD.rule_id, OLD.project_id, OLD.adr_id, OLD.source_element, OLD.source_element_type,
           OLD.relationship_type, OLD.target_element, OLD.target_element_type, OLD.decision,
           OLD.description, OLD.severity)
       IS DISTINCT FROM
       ROW(NEW.rule_id, NEW.project_id, NEW.adr_id, NEW.source_element, NEW.source_element_type,
           NEW.relationship_type, NEW.target_element, NEW.target_element_type, NEW.decision,
           NEW.description, NEW.severity) THEN
        PERFORM advance_rule_evidence(NEW.id,
            ROW(OLD.project_id, OLD.source_element_type, OLD.relationship_type, OLD.target_element_type)
            IS DISTINCT FROM
            ROW(NEW.project_id, NEW.source_element_type, NEW.relationship_type, NEW.target_element_type));
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER architecture_rules_evidence_changed AFTER UPDATE ON architecture_rules
FOR EACH ROW EXECUTE FUNCTION track_rule_evidence();

CREATE FUNCTION track_project_style_evidence() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE affected UUID; styles_changed BOOLEAN;
BEGIN
    styles_changed := ARRAY(SELECT DISTINCT unnest(OLD.architecture_styles) ORDER BY 1)
       IS DISTINCT FROM ARRAY(SELECT DISTINCT unnest(NEW.architecture_styles) ORDER BY 1);
    IF styles_changed OR OLD.repository_url IS DISTINCT FROM NEW.repository_url THEN
        FOR affected IN SELECT id FROM architecture_rules WHERE project_id = NEW.id ORDER BY id LOOP
            PERFORM advance_rule_evidence(affected, styles_changed);
        END LOOP;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER registered_projects_style_evidence_changed AFTER UPDATE ON registered_projects
FOR EACH ROW EXECUTE FUNCTION track_project_style_evidence();

CREATE FUNCTION track_adr_review_evidence() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE affected UUID;
BEGIN
    IF ROW(OLD.adr_id, OLD.title, OLD.status, OLD.context, OLD.decision, OLD.consequences, OLD.version)
       IS DISTINCT FROM ROW(NEW.adr_id, NEW.title, NEW.status, NEW.context, NEW.decision, NEW.consequences, NEW.version) THEN
        FOR affected IN SELECT id FROM architecture_rules WHERE adr_id = NEW.id ORDER BY id LOOP
            PERFORM advance_rule_evidence(affected, FALSE);
        END LOOP;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER adrs_review_evidence_changed AFTER UPDATE ON adrs
FOR EACH ROW EXECUTE FUNCTION track_adr_review_evidence();

CREATE FUNCTION track_mapping_review_evidence() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM advance_rule_evidence(OLD.rule_id, FALSE);
        RETURN OLD;
    ELSIF TG_OP = 'INSERT' THEN
        PERFORM advance_rule_evidence(NEW.rule_id, FALSE);
    ELSIF ROW(OLD.rule_id, OLD.side, OLD.file_path) IS DISTINCT FROM ROW(NEW.rule_id, NEW.side, NEW.file_path) THEN
        PERFORM advance_rule_evidence(OLD.rule_id, FALSE);
        IF OLD.rule_id <> NEW.rule_id THEN PERFORM advance_rule_evidence(NEW.rule_id, FALSE); END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER implementation_mappings_review_evidence_changed
AFTER INSERT OR UPDATE OR DELETE ON rule_implementation_mappings
FOR EACH ROW EXECUTE FUNCTION track_mapping_review_evidence();
