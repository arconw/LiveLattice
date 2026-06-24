CREATE TABLE IF NOT EXISTS background_job_definitions (
    id UUID PRIMARY KEY,
    job_type VARCHAR(100) NOT NULL,
    workspace_id UUID,
    payload_jsonb JSONB,
    priority INT NOT NULL DEFAULT 50,
    max_retries INT NOT NULL DEFAULT 3,
    retry_delay_seconds INT NOT NULL DEFAULT 60,
    retry_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    owner_subject VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE,
    next_retry_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_bgd_jobs_status_type ON background_job_definitions(status, job_type);
CREATE INDEX IF NOT EXISTS idx_bgd_jobs_workspace_id ON background_job_definitions(workspace_id);
CREATE INDEX IF NOT EXISTS idx_bgd_jobs_next_retry_at ON background_job_definitions(next_retry_at) WHERE status = 'RETRYING';
CREATE INDEX IF NOT EXISTS idx_bgd_jobs_scheduled_at ON background_job_definitions(scheduled_at) WHERE status = 'SCHEDULED';

CREATE TABLE IF NOT EXISTS background_job_executions (
    id UUID PRIMARY KEY,
    job_definition_id UUID NOT NULL REFERENCES background_job_definitions(id),
    status VARCHAR(20) NOT NULL,
    worker_id VARCHAR(100),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    progress INT NOT NULL DEFAULT 0,
    result_jsonb JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bgd_exec_definition_id ON background_job_executions(job_definition_id);

CREATE TABLE IF NOT EXISTS background_job_dead_letters (
    id UUID PRIMARY KEY,
    job_definition_id UUID NOT NULL REFERENCES background_job_definitions(id),
    failed_execution_id UUID REFERENCES background_job_executions(id),
    error_message TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bgd_dl_job_id ON background_job_dead_letters(job_definition_id);
