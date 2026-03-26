-- Create Schemas
CREATE SCHEMA IF NOT EXISTS hiring;

-- Users/Auth Schema
CREATE TABLE hiring.users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_by VARCHAR(36)
);

-- Candidates Schema
CREATE TABLE hiring.candidates (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    location VARCHAR(255),
    summary TEXT,
    years_of_experience FLOAT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    FOREIGN KEY (user_id) REFERENCES hiring.users(id) ON DELETE CASCADE
);

-- Resumes Schema
CREATE TABLE hiring.resumes (
    id VARCHAR(36) PRIMARY KEY,
    candidate_id VARCHAR(36) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500),
    file_size BIGINT,
    file_type VARCHAR(50),
    parsed_data JSONB,
    parse_status VARCHAR(50),
    parsed_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    FOREIGN KEY (candidate_id) REFERENCES hiring.candidates(id) ON DELETE CASCADE,
    INDEX idx_candidate_id (candidate_id),
    INDEX idx_parse_status (parse_status)
);

-- Jobs Schema
CREATE TABLE hiring.jobs (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    requirements TEXT,
    location VARCHAR(255),
    salary_min DECIMAL(12, 2),
    salary_max DECIMAL(12, 2),
    currency VARCHAR(10),
    job_type VARCHAR(50),
    company_id VARCHAR(36),
    posted_by VARCHAR(36),
    status VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    INDEX idx_status (status),
    INDEX idx_posted_by (posted_by)
);

-- Candidate Matches Schema
CREATE TABLE hiring.candidate_matches (
    id VARCHAR(36) PRIMARY KEY,
    candidate_id VARCHAR(36) NOT NULL,
    job_id VARCHAR(36) NOT NULL,
    match_score FLOAT,
    match_status VARCHAR(50),
    matched_skills JSON,
    missing_skills JSON,
    recommendation TEXT,
    matched_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    FOREIGN KEY (candidate_id) REFERENCES hiring.candidates(id) ON DELETE CASCADE,
    FOREIGN KEY (job_id) REFERENCES hiring.jobs(id) ON DELETE CASCADE,
    UNIQUE KEY uk_candidate_job (candidate_id, job_id),
    INDEX idx_match_status (match_status),
    INDEX idx_match_score (match_score)
);

-- Interview Schedule Schema
CREATE TABLE hiring.interview_schedules (
    id VARCHAR(36) PRIMARY KEY,
    candidate_id VARCHAR(36) NOT NULL,
    job_id VARCHAR(36) NOT NULL,
    interview_type VARCHAR(50),
    scheduled_datetime TIMESTAMP NOT NULL,
    duration INTEGER,
    interviewer_name VARCHAR(255),
    location VARCHAR(255),
    meeting_link VARCHAR(500),
    status VARCHAR(50),
    feedback TEXT,
    rating FLOAT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    FOREIGN KEY (candidate_id) REFERENCES hiring.candidates(id) ON DELETE CASCADE,
    FOREIGN KEY (job_id) REFERENCES hiring.jobs(id) ON DELETE CASCADE,
    INDEX idx_scheduled_datetime (scheduled_datetime),
    INDEX idx_status (status)
);

-- Skills Master Data
CREATE TABLE hiring.skills (
    id VARCHAR(36) PRIMARY KEY,
    skill_name VARCHAR(255) NOT NULL UNIQUE,
    category VARCHAR(100),
    proficiency_level VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_skill_name (skill_name)
);

-- Candidate Skills
CREATE TABLE hiring.candidate_skills (
    id VARCHAR(36) PRIMARY KEY,
    candidate_id VARCHAR(36) NOT NULL,
    skill_id VARCHAR(36) NOT NULL,
    proficiency_level VARCHAR(50),
    years_of_experience FLOAT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (candidate_id) REFERENCES hiring.candidates(id) ON DELETE CASCADE,
    FOREIGN KEY (skill_id) REFERENCES hiring.skills(id) ON DELETE CASCADE,
    UNIQUE KEY uk_candidate_skill (candidate_id, skill_id),
    INDEX idx_candidate_id (candidate_id)
);

-- Job Analyses (for storing parsed JD data)
CREATE TABLE hiring.job_analyses (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    required_skills JSON,
    required_experience FLOAT,
    analysis_data JSONB,
    salary_prediction DECIMAL(12, 2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    FOREIGN KEY (job_id) REFERENCES hiring.jobs(id) ON DELETE CASCADE,
    INDEX idx_job_id (job_id)
);

-- Create Indexes
CREATE INDEX idx_users_email ON hiring.users(email);
CREATE INDEX idx_users_username ON hiring.users(username);
CREATE INDEX idx_candidates_email ON hiring.candidates(email);
CREATE INDEX idx_jobs_status ON hiring.jobs(status);

-- Grant Permissions
GRANT ALL PRIVILEGES ON SCHEMA hiring TO hiring_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA hiring TO hiring_user;

-- ==========================================
-- Phase 4: Reliable Eventing + Consistency
-- ==========================================

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

CREATE INDEX IF NOT EXISTS idx_event_outbox_status_created
    ON hiring.event_outbox(status, created_at);

CREATE TABLE IF NOT EXISTS hiring.processed_events (
    id BIGSERIAL PRIMARY KEY,
    event_key VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_processed_events_type_time
    ON hiring.processed_events(event_type, processed_at);

CREATE TABLE IF NOT EXISTS hiring.saga_state (
    id BIGSERIAL PRIMARY KEY,
    saga_id VARCHAR(128) NOT NULL UNIQUE,
    workflow_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    state VARCHAR(64) NOT NULL,
    steps_completed JSONB,
    failure_reasons JSONB,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_saga_state_aggregate
    ON hiring.saga_state(aggregate_id);

