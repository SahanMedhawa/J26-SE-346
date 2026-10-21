CREATE TABLE rule_implementation_mappings (
    id UUID PRIMARY KEY,
    rule_id UUID NOT NULL,
    side VARCHAR(6) NOT NULL,
    file_path TEXT NOT NULL,
    CONSTRAINT rule_implementation_mappings_rule_fk
        FOREIGN KEY (rule_id) REFERENCES architecture_rules (id) ON DELETE CASCADE,
    CONSTRAINT rule_implementation_mappings_side_ck CHECK (side IN ('SOURCE', 'TARGET')),
    CONSTRAINT rule_implementation_mappings_path_ck CHECK (btrim(file_path) <> ''),
    CONSTRAINT rule_implementation_mappings_rule_side_path_uq UNIQUE (rule_id, side, file_path)
);

-- The unique index starts with rule_id and also supports retrieval by rule.
