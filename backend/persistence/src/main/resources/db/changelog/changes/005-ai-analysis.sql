--liquibase formatted sql
--changeset ai-lead-manager:005-ai-analysis
CREATE TABLE "AI_LEAD_MANAGER".ai_jobs (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lead_id bigint NOT NULL REFERENCES "AI_LEAD_MANAGER".leads(id) ON DELETE CASCADE,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    attempt_count integer NOT NULL DEFAULT 0,
    max_attempts integer NOT NULL DEFAULT 5,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    locked_at timestamptz,
    locked_until timestamptz,
    locked_by varchar(100),
    last_error_code varchar(64),
    last_error_message varchar(1000),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ai_jobs_status_valid CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ai_jobs_attempts_valid CHECK (attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts),
    CONSTRAINT ai_jobs_lock_valid CHECK (
        (status = 'RUNNING' AND locked_at IS NOT NULL AND locked_until IS NOT NULL AND locked_by IS NOT NULL)
        OR
        (status <> 'RUNNING' AND locked_at IS NULL AND locked_until IS NULL AND locked_by IS NULL)
    )
);

CREATE UNIQUE INDEX ai_jobs_one_active_per_lead_idx
    ON "AI_LEAD_MANAGER".ai_jobs (lead_id)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE INDEX ai_jobs_available_idx
    ON "AI_LEAD_MANAGER".ai_jobs (next_attempt_at, created_at, id)
    WHERE status = 'PENDING';

CREATE INDEX ai_jobs_stale_idx
    ON "AI_LEAD_MANAGER".ai_jobs (locked_until)
    WHERE status = 'RUNNING';

CREATE TABLE "AI_LEAD_MANAGER".ai_results (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id bigint NOT NULL UNIQUE REFERENCES "AI_LEAD_MANAGER".ai_jobs(id) ON DELETE CASCADE,
    lead_id bigint NOT NULL REFERENCES "AI_LEAD_MANAGER".leads(id) ON DELETE CASCADE,
    summary varchar(1000) NOT NULL,
    extracted_facts jsonb NOT NULL,
    missing_fields jsonb NOT NULL,
    suggested_question varchar(1000),
    priority varchar(16) NOT NULL,
    priority_reason varchar(500) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ai_results_priority_valid CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ai_results_summary_not_blank CHECK (length(btrim(summary)) > 0),
    CONSTRAINT ai_results_reason_not_blank CHECK (length(btrim(priority_reason)) > 0),
    CONSTRAINT ai_results_facts_object CHECK (jsonb_typeof(extracted_facts) = 'object'),
    CONSTRAINT ai_results_missing_array CHECK (jsonb_typeof(missing_fields) = 'array')
);

CREATE INDEX ai_results_lead_created_at_idx
    ON "AI_LEAD_MANAGER".ai_results (lead_id, created_at DESC, id DESC);
