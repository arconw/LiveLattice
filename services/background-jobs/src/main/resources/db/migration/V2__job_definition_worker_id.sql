ALTER TABLE background_job_definitions ADD COLUMN IF NOT EXISTS worker_id VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_bgd_jobs_worker_id ON background_job_definitions(worker_id) WHERE status = 'RUNNING';
