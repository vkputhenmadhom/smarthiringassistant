-- Resume Parser schema bootstrap (idempotent)

CREATE SCHEMA IF NOT EXISTS hiring;

CREATE TABLE IF NOT EXISTS hiring.resume_parser_parsed_data (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(255),
    location VARCHAR(255),
    summary TEXT,
    total_experience_years DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS hiring.resume_parser_experience (
    id BIGSERIAL PRIMARY KEY,
    parsed_data_id BIGINT,
    company VARCHAR(255),
    job_title VARCHAR(255),
    start_date VARCHAR(255),
    end_date VARCHAR(255),
    description TEXT,
    duration_years DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS hiring.resume_parser_education (
    id BIGSERIAL PRIMARY KEY,
    parsed_data_id BIGINT,
    institution VARCHAR(255),
    degree VARCHAR(255),
    field_of_study VARCHAR(255),
    graduation_date VARCHAR(255),
    gpa VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS hiring.resume_parser_skills (
    parsed_data_id BIGINT NOT NULL,
    skill VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS hiring.resume_parser_certifications (
    parsed_data_id BIGINT NOT NULL,
    certification VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS hiring.resume_parser_resumes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resume_id VARCHAR(255) NOT NULL UNIQUE,
    file_name VARCHAR(255) NOT NULL,
    file_content BYTEA NOT NULL,
    file_format VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    error_message VARCHAR(255),
    parsed_data_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    parsed_at TIMESTAMP
);

ALTER TABLE hiring.resume_parser_resumes ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE hiring.resume_parser_resumes ADD COLUMN IF NOT EXISTS resume_id VARCHAR(255);
ALTER TABLE hiring.resume_parser_resumes ADD COLUMN IF NOT EXISTS file_name VARCHAR(255);
ALTER TABLE hiring.resume_parser_resumes ADD COLUMN IF NOT EXISTS file_content BYTEA;
ALTER TABLE hiring.resume_parser_resumes ADD COLUMN IF NOT EXISTS file_format VARCHAR(255);
ALTER TABLE hiring.resume_parser_resumes ADD COLUMN IF NOT EXISTS status VARCHAR(50);
ALTER TABLE hiring.resume_parser_resumes ADD COLUMN IF NOT EXISTS error_message VARCHAR(255);
ALTER TABLE hiring.resume_parser_resumes ADD COLUMN IF NOT EXISTS parsed_data_id BIGINT;
ALTER TABLE hiring.resume_parser_resumes ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE hiring.resume_parser_resumes ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE hiring.resume_parser_resumes ADD COLUMN IF NOT EXISTS parsed_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS hiring.resume_parser_event_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    exchange_name VARCHAR(255) NOT NULL,
    routing_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP
);

