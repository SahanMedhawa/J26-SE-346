ALTER TABLE adrs
    ADD CONSTRAINT adrs_id_project_uq UNIQUE (id, project_id);

CREATE TABLE architecture_rules (
    id UUID PRIMARY KEY,
    rule_id VARCHAR(64) NOT NULL,
    project_id UUID NOT NULL,
    adr_id UUID NOT NULL,
    source_element VARCHAR(200) NOT NULL,
    source_element_type VARCHAR(32) NOT NULL,
    relationship_type VARCHAR(32) NOT NULL,
    target_element VARCHAR(200) NOT NULL,
    target_element_type VARCHAR(32) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    description TEXT NOT NULL,
    severity VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT architecture_rules_project_fk
        FOREIGN KEY (project_id) REFERENCES registered_projects (id),
    CONSTRAINT architecture_rules_adr_fk
        FOREIGN KEY (adr_id, project_id) REFERENCES adrs (id, project_id),
    CONSTRAINT architecture_rules_project_rule_id_uq
        UNIQUE (project_id, rule_id),
    CONSTRAINT architecture_rules_source_type_ck CHECK
        (source_element_type IN ('SERVICE', 'DATABASE', 'CONTROLLER', 'SERVICE_LAYER',
         'REPOSITORY', 'MESSAGE_BROKER', 'API_GATEWAY', 'EXTERNAL_SYSTEM')),
    CONSTRAINT architecture_rules_target_type_ck CHECK
        (target_element_type IN ('SERVICE', 'DATABASE', 'CONTROLLER', 'SERVICE_LAYER',
         'REPOSITORY', 'MESSAGE_BROKER', 'API_GATEWAY', 'EXTERNAL_SYSTEM')),
    CONSTRAINT architecture_rules_relationship_ck CHECK
        (relationship_type IN ('CALLS', 'DEPENDS_ON', 'DIRECT_DATABASE_ACCESS', 'DATA_ACCESS',
         'PUBLISHES_TO', 'CONSUMES_FROM', 'USES_API')),
    CONSTRAINT architecture_rules_decision_ck CHECK
        (decision IN ('ALLOWED', 'FORBIDDEN', 'REQUIRED')),
    CONSTRAINT architecture_rules_severity_ck CHECK
        (severity IN ('HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX architecture_rules_project_idx ON architecture_rules (project_id);
CREATE INDEX architecture_rules_adr_idx ON architecture_rules (adr_id);
