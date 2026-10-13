CREATE TABLE registered_projects (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(2000),
    repository_url TEXT NOT NULL,
    architecture_styles TEXT[] NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL,
    CONSTRAINT registered_projects_repository_url_uq
        UNIQUE (repository_url),
    CONSTRAINT registered_projects_status_ck
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT registered_projects_architecture_styles_ck
        CHECK (cardinality(architecture_styles) > 0)
);

CREATE INDEX registered_projects_registered_at_idx
    ON registered_projects (registered_at);
