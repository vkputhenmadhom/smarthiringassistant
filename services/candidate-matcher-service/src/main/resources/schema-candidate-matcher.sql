CREATE SCHEMA IF NOT EXISTS hiring;

CREATE TABLE IF NOT EXISTS hiring.job_requirements (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(255) NOT NULL UNIQUE,
    job_title VARCHAR(255) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    required_years_experience INTEGER NOT NULL,
    location VARCHAR(255),
    job_description TEXT,
    status VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS hiring.job_required_skills (
    job_id BIGINT NOT NULL,
    skill VARCHAR(255),
    CONSTRAINT fk_job_required_skills_job_requirement
        FOREIGN KEY (job_id) REFERENCES hiring.job_requirements(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS hiring.candidate_matches (
    id BIGSERIAL PRIMARY KEY,
    match_id VARCHAR(255) NOT NULL UNIQUE,
    candidate_id BIGINT NOT NULL,
    job_id VARCHAR(255) NOT NULL,
    overall_score DOUBLE PRECISION NOT NULL,
    skill_match_percentage DOUBLE PRECISION NOT NULL,
    experience_match_percentage DOUBLE PRECISION,
    location_match_percentage DOUBLE PRECISION,
    status VARCHAR(64),
    reviewer_comments VARCHAR(1000),
    matched_skills TEXT,
    missing_skills TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Existing databases: skill columns were added later.
ALTER TABLE hiring.candidate_matches ADD COLUMN IF NOT EXISTS matched_skills TEXT;
ALTER TABLE hiring.candidate_matches ADD COLUMN IF NOT EXISTS missing_skills TEXT;

-- Experience and location are null when the job or candidate leaves them unknown.
ALTER TABLE hiring.candidate_matches ALTER COLUMN experience_match_percentage DROP NOT NULL;
ALTER TABLE hiring.candidate_matches ALTER COLUMN location_match_percentage DROP NOT NULL;

-- Match statuses now follow the GraphQL MatchStatus (PENDING, MATCHED, SHORTLISTED, REJECTED, HIRED).
UPDATE hiring.candidate_matches SET status = 'MATCHED' WHERE status IN ('NEW', 'REVIEWED') OR status IS NULL;
UPDATE hiring.candidate_matches SET status = 'SHORTLISTED' WHERE status = 'ACCEPTED';

CREATE INDEX IF NOT EXISTS idx_candidate_matches_candidate_id
    ON hiring.candidate_matches(candidate_id);
CREATE INDEX IF NOT EXISTS idx_candidate_matches_job_id
    ON hiring.candidate_matches(job_id);

CREATE TABLE IF NOT EXISTS hiring.processed_events (
    id BIGSERIAL PRIMARY KEY,
    event_key VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS hiring.event_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    exchange_name VARCHAR(255) NOT NULL,
    routing_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP
);


-- Most years a job is meant for (stated, or its title's level); null: no upper bound or unknown.
ALTER TABLE hiring.job_requirements ADD COLUMN IF NOT EXISTS max_years_experience INTEGER;
