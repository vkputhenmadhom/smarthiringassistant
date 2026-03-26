db.createUser({
    user: "hiring_user",
    pwd: "hiring_password",
    roles: [
        {
            role: "readWrite",
            db: "smart_hiring_db"
        }
    ]
});

// Create collections
db.createCollection("resumes");
db.createCollection("candidate_profiles");
db.createCollection("job_postings");
db.createCollection("interview_questions");
db.createCollection("screening_responses");
db.createCollection("ai_embeddings");
db.createCollection("consumed_events");
db.createCollection("workflow_saga_state");
db.createCollection("event_replay_log");

// Create indexes
db.resumes.createIndex({ candidateId: 1 });
db.resumes.createIndex({ uploadDate: -1 });
db.candidate_profiles.createIndex({ email: 1 }, { unique: true });
db.job_postings.createIndex({ status: 1 });
db.job_postings.createIndex({ postedDate: -1 });
db.interview_questions.createIndex({ questionType: 1 });
db.interview_questions.createIndex({ difficulty: 1 });
db.screening_responses.createIndex({ candidateId: 1 });
db.screening_responses.createIndex({ jobId: 1 });
db.ai_embeddings.createIndex({ type: 1 });
db.consumed_events.createIndex({ eventKey: 1 }, { unique: true });
db.consumed_events.createIndex({ consumedAt: -1 });
db.workflow_saga_state.createIndex({ sagaId: 1 }, { unique: true });
db.workflow_saga_state.createIndex({ aggregateId: 1 });
db.workflow_saga_state.createIndex({ state: 1 });
db.event_replay_log.createIndex({ status: 1, createdAt: -1 });

print("MongoDB initialization completed");

