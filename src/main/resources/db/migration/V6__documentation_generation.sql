-- DocSync Documentation Generation Schema
-- V6: Generation runs and generated documentation changes

CREATE TABLE generation_runs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    analysis_id UUID REFERENCES analyses(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE generated_documentation_changes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    generation_run_id UUID NOT NULL REFERENCES generation_runs(id) ON DELETE CASCADE,
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    analysis_id UUID REFERENCES analyses(id) ON DELETE SET NULL,
    file_path VARCHAR(512) NOT NULL,
    change_type VARCHAR(20) NOT NULL DEFAULT 'UPDATE',
    original_content TEXT,
    generated_content TEXT NOT NULL,
    diff_content TEXT,
    provenance TEXT,
    confidence DECIMAL(3,2),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version_lock BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_generated_changes_repo_status ON generated_documentation_changes(repository_id, status);
CREATE INDEX idx_generated_changes_run ON generated_documentation_changes(generation_run_id);