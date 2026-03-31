-- Resume Parser schema bootstrap (idempotent)

CREATE TABLE IF NOT EXISTS parsed_resume_data (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(255),
    location VARCHAR(255),
    summary TEXT,
    total_experience_years DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS resume_experience (
    id BIGSERIAL PRIMARY KEY,
    parsed_data_id BIGINT,
    company VARCHAR(255),
    job_title VARCHAR(255),
    start_date VARCHAR(255),
    end_date VARCHAR(255),
    description TEXT,
    duration_years DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS resume_education (
    id BIGSERIAL PRIMARY KEY,
    parsed_data_id BIGINT,
    institution VARCHAR(255),
    degree VARCHAR(255),
    field_of_study VARCHAR(255),
    graduation_date VARCHAR(255),
    gpa VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS resume_skills (
    parsed_data_id BIGINT NOT NULL,
    skill VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS resume_certifications (
    parsed_data_id BIGINT NOT NULL,
    certification VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS resumes (
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

ALTER TABLE resumes ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS resume_id VARCHAR(255);
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS file_name VARCHAR(255);
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS file_content BYTEA;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS file_format VARCHAR(255);
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS status VARCHAR(50);
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS error_message VARCHAR(255);
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS parsed_data_id BIGINT;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS parsed_at TIMESTAMP;

