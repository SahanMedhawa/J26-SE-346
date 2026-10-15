CREATE TABLE adrs (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    adr_id VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL,
    context TEXT NOT NULL,
    decision TEXT NOT NULL,
    consequences TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT adrs_project_fk
        FOREIGN KEY (project_id) REFERENCES registered_projects (id),
    CONSTRAINT adrs_project_adr_id_uq
        UNIQUE (project_id, adr_id),
    CONSTRAINT adrs_status_ck
        CHECK (status IN ('PROPOSED', 'ACCEPTED', 'DEPRECATED', 'SUPERSEDED')),
    CONSTRAINT adrs_version_ck
        CHECK (version >= 1)
);

CREATE INDEX adrs_project_idx ON adrs (project_id);
CREATE INDEX adrs_status_idx ON adrs (status);
CREATE INDEX adrs_search_idx ON adrs (project_id, title, adr_id);
