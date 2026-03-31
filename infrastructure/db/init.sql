-- PostgreSQL bootstrap schema for Smart Hiring Assistant

CREATE SCHEMA IF NOT EXISTS hiring;

-- Users / Auth
CREATE TABLE IF NOT EXISTS hiring.users (
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

-- Candidates
CREATE TABLE IF NOT EXISTS hiring.candidates (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    location VARCHAR(255),
    summary TEXT,
    years_of_experience DOUBLE PRECISION,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    CONSTRAINT fk_candidates_user FOREIGN KEY (user_id) REFERENCES hiring.users(id) ON DELETE CASCADE
);

-- Resumes
CREATE TABLE IF NOT EXISTS hiring.resumes (
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
    CONSTRAINT fk_resumes_candidate FOREIGN KEY (candidate_id) REFERENCES hiring.candidates(id) ON DELETE CASCADE
);

-- Jobs
CREATE TABLE IF NOT EXISTS hiring.jobs (
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
    updated_by VARCHAR(36)
);

-- Candidate matches
CREATE TABLE IF NOT EXISTS hiring.candidate_matches (
    id VARCHAR(36) PRIMARY KEY,
    candidate_id VARCHAR(36) NOT NULL,
    job_id VARCHAR(36) NOT NULL,
    match_score DOUBLE PRECISION,
    match_status VARCHAR(50),
    matched_skills JSONB,
    missing_skills JSONB,
    recommendation TEXT,
    matched_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    CONSTRAINT fk_candidate_matches_candidate FOREIGN KEY (candidate_id) REFERENCES hiring.candidates(id) ON DELETE CASCADE,
    CONSTRAINT fk_candidate_matches_job FOREIGN KEY (job_id) REFERENCES hiring.jobs(id) ON DELETE CASCADE,
    CONSTRAINT uk_candidate_job UNIQUE (candidate_id, job_id)
);

-- Interview schedules
CREATE TABLE IF NOT EXISTS hiring.interview_schedules (
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
    rating DOUBLE PRECISION,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    CONSTRAINT fk_interview_schedules_candidate FOREIGN KEY (candidate_id) REFERENCES hiring.candidates(id) ON DELETE CASCADE,
    CONSTRAINT fk_interview_schedules_job FOREIGN KEY (job_id) REFERENCES hiring.jobs(id) ON DELETE CASCADE
);

-- Skills
CREATE TABLE IF NOT EXISTS hiring.skills (
    id VARCHAR(36) PRIMARY KEY,
    skill_name VARCHAR(255) NOT NULL UNIQUE,
    category VARCHAR(100),
    proficiency_level VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Candidate skills
CREATE TABLE IF NOT EXISTS hiring.candidate_skills (
    id VARCHAR(36) PRIMARY KEY,
    candidate_id VARCHAR(36) NOT NULL,
    skill_id VARCHAR(36) NOT NULL,
    proficiency_level VARCHAR(50),
    years_of_experience DOUBLE PRECISION,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_candidate_skills_candidate FOREIGN KEY (candidate_id) REFERENCES hiring.candidates(id) ON DELETE CASCADE,
    CONSTRAINT fk_candidate_skills_skill FOREIGN KEY (skill_id) REFERENCES hiring.skills(id) ON DELETE CASCADE,
    CONSTRAINT uk_candidate_skill UNIQUE (candidate_id, skill_id)
);

-- Job analyses
CREATE TABLE IF NOT EXISTS hiring.job_analyses (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    required_skills JSONB,
    required_experience DOUBLE PRECISION,
    analysis_data JSONB,
    salary_prediction DECIMAL(12, 2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    CONSTRAINT fk_job_analyses_job FOREIGN KEY (job_id) REFERENCES hiring.jobs(id) ON DELETE CASCADE
);

-- Reliable eventing + consistency
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

CREATE TABLE IF NOT EXISTS hiring.processed_events (
    id BIGSERIAL PRIMARY KEY,
    event_key VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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

-- Indexes
CREATE INDEX IF NOT EXISTS idx_users_email ON hiring.users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON hiring.users(username);
CREATE INDEX IF NOT EXISTS idx_candidates_email ON hiring.candidates(email);
CREATE INDEX IF NOT EXISTS idx_resumes_candidate_id ON hiring.resumes(candidate_id);
CREATE INDEX IF NOT EXISTS idx_resumes_parse_status ON hiring.resumes(parse_status);
CREATE INDEX IF NOT EXISTS idx_jobs_status ON hiring.jobs(status);
CREATE INDEX IF NOT EXISTS idx_jobs_posted_by ON hiring.jobs(posted_by);
CREATE INDEX IF NOT EXISTS idx_candidate_matches_status ON hiring.candidate_matches(match_status);
CREATE INDEX IF NOT EXISTS idx_candidate_matches_score ON hiring.candidate_matches(match_score);
CREATE INDEX IF NOT EXISTS idx_interview_schedules_datetime ON hiring.interview_schedules(scheduled_datetime);
CREATE INDEX IF NOT EXISTS idx_interview_schedules_status ON hiring.interview_schedules(status);
CREATE INDEX IF NOT EXISTS idx_skills_name ON hiring.skills(skill_name);
CREATE INDEX IF NOT EXISTS idx_candidate_skills_candidate_id ON hiring.candidate_skills(candidate_id);
CREATE INDEX IF NOT EXISTS idx_job_analyses_job_id ON hiring.job_analyses(job_id);
CREATE INDEX IF NOT EXISTS idx_event_outbox_status_created ON hiring.event_outbox(status, created_at);
CREATE INDEX IF NOT EXISTS idx_processed_events_type_time ON hiring.processed_events(event_type, processed_at);
CREATE INDEX IF NOT EXISTS idx_saga_state_aggregate ON hiring.saga_state(aggregate_id);

-- Permissions
GRANT USAGE ON SCHEMA hiring TO hiring_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA hiring TO hiring_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA hiring TO hiring_user;

