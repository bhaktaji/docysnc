-- DocSync Analysis Engine Schema
-- V5: Analyses, code entities, documentation pages, references, and findings

CREATE TABLE analyses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    job_id UUID REFERENCES analysis_jobs(id) ON DELETE SET NULL,
    branch VARCHAR(255) NOT NULL,
    commit_sha VARCHAR(40) NOT NULL,
    scope VARCHAR(20) NOT NULL DEFAULT 'FULL',
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    stats JSONB,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_analyses_repository_created ON analyses(repository_id, created_at DESC);

CREATE TABLE code_entities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    analysis_id UUID NOT NULL REFERENCES analyses(id) ON DELETE CASCADE,
    entity_type VARCHAR(30) NOT NULL,
    qualified_name VARCHAR(1000) NOT NULL,
    simple_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    line_number INTEGER,
    modifiers VARCHAR(100),
    signature TEXT,
    signature_hash VARCHAR(64),
    javadoc TEXT,
    parent_entity_id UUID REFERENCES code_entities(id) ON DELETE SET NULL,
    commit_sha VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_code_entities_repo_commit ON code_entities(repository_id, commit_sha);
CREATE INDEX idx_code_entities_qualified_name ON code_entities(repository_id, qualified_name);

CREATE TABLE documentation_pages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    analysis_id UUID NOT NULL REFERENCES analyses(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(30) NOT NULL,
    path VARCHAR(512) NOT NULL,
    content_hash VARCHAR(64),
    last_analyzed_commit VARCHAR(40),
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_documentation_pages_repo_path UNIQUE (repository_id, path)
);

CREATE TABLE documentation_code_references (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    page_id UUID NOT NULL REFERENCES documentation_pages(id) ON DELETE CASCADE,
    code_entity_id UUID REFERENCES code_entities(id) ON DELETE SET NULL,
    qualified_name VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_doc_code_refs_page ON documentation_code_references(page_id);
CREATE INDEX idx_doc_code_refs_entity ON documentation_code_references(code_entity_id);

CREATE TABLE findings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    analysis_id UUID NOT NULL REFERENCES analyses(id) ON DELETE CASCADE,
    severity VARCHAR(20) NOT NULL,
    category VARCHAR(30) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    entity_id UUID REFERENCES code_entities(id) ON DELETE SET NULL,
    file_path VARCHAR(1000),
    line_number INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_findings_repo_status ON findings(repository_id, status, severity);