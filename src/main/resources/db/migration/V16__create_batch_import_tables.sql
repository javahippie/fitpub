-- Migration V16: Create batch import tables for asynchronous ZIP file processing
-- Purpose: Support batch import of hundreds of FIT/GPX files with progress tracking

-- Main job tracking table
CREATE TABLE batch_import_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filename VARCHAR(500) NOT NULL,
    total_files INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    processed_files INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    skip_federation BOOLEAN NOT NULL DEFAULT TRUE,

    -- Constraints
    CONSTRAINT batch_import_jobs_status_check CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT batch_import_jobs_total_files_positive CHECK (total_files > 0),
    CONSTRAINT batch_import_jobs_processed_files_non_negative CHECK (processed_files >= 0),
    CONSTRAINT batch_import_jobs_counts_non_negative CHECK (
        success_count >= 0 AND
        failed_count >= 0 AND
        skipped_count >= 0
    )
);

-- Individual file result tracking table
CREATE TABLE batch_import_file_results (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES batch_import_jobs(id) ON DELETE CASCADE,
    filename VARCHAR(500) NOT NULL,
    file_size BIGINT,
    status VARCHAR(50) NOT NULL,
    activity_id UUID REFERENCES activities(id) ON DELETE SET NULL,
    error_message TEXT,
    error_type VARCHAR(100),
    processed_at TIMESTAMP,

    -- Constraints
    CONSTRAINT batch_import_file_results_status_check CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'SKIPPED')),
    CONSTRAINT batch_import_file_results_file_size_non_negative CHECK (file_size IS NULL OR file_size >= 0)
);

-- Indexes for performance
CREATE INDEX idx_batch_import_jobs_user_id ON batch_import_jobs(user_id);
CREATE INDEX idx_batch_import_jobs_created_at ON batch_import_jobs(created_at);
CREATE INDEX idx_batch_import_jobs_status ON batch_import_jobs(status);
CREATE INDEX idx_batch_import_file_results_job_id ON batch_import_file_results(job_id);
CREATE INDEX idx_batch_import_file_results_status ON batch_import_file_results(status);

-- Comments for documentation
COMMENT ON TABLE batch_import_jobs IS 'Tracks asynchronous batch import jobs for ZIP files containing multiple activity files';
COMMENT ON TABLE batch_import_file_results IS 'Tracks processing results for individual files within a batch import job';
COMMENT ON COLUMN batch_import_jobs.skip_federation IS 'When true, imported activities will not trigger ActivityPub federation';
COMMENT ON COLUMN batch_import_jobs.total_files IS 'Total number of files extracted from ZIP';
COMMENT ON COLUMN batch_import_jobs.processed_files IS 'Number of files processed (success + failed + skipped)';
COMMENT ON COLUMN batch_import_file_results.error_type IS 'Category of error (e.g., VALIDATION_ERROR, PARSING_ERROR, IO_ERROR)';
