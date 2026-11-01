-- DocSync Repository Onboarding Schema
-- V3: GitHub App connections, repositories, and persisted webhook deliveries

CREATE TABLE repository_connections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    installation_id BIGINT NOT NULL,
    account_login VARCHAR(255),
    account_type VARCHAR(50),
    installer_github_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_connections_provider_installation UNIQUE (provider, installation_id)
);

CREATE INDEX idx_connections_org_id ON repository_connections(organization_id);
CREATE INDEX idx_connections_status ON repository_connections(status);

CREATE TABLE repositories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL REFERENCES repository_connections(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    installation_id BIGINT NOT NULL,
    external_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    full_name VARCHAR(512) NOT NULL,
    clone_url VARCHAR(1024),
    default_branch VARCHAR(100) NOT NULL DEFAULT 'main',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    configuration JSONB NOT NULL DEFAULT '{}',
    error_message TEXT,
    last_synced_at TIMESTAMP WITH TIME ZONE,
    last_commit_sha VARCHAR(40),
    last_analysis_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_repositories_org_external UNIQUE (organization_id, provider, external_id)
);

CREATE INDEX idx_repositories_org_id ON repositories(organization_id);
CREATE INDEX idx_repositories_connection_id ON repositories(connection_id);
CREATE INDEX idx_repositories_status ON repositories(status);

CREATE TABLE webhook_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    provider VARCHAR(20) NOT NULL,
    delivery_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    repository_id UUID,
    payload JSONB NOT NULL,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    error_message TEXT,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_webhook_provider_delivery UNIQUE (provider, delivery_id)
);

CREATE INDEX idx_webhook_events_repository ON webhook_events(repository_id);
CREATE INDEX idx_webhook_events_status ON webhook_events(processing_status);
