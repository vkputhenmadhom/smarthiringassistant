package org.vinod.sha.auth.entity;

public enum UserRole {
    ADMIN,
    RECRUITER,
    JOB_SEEKER,
    HIRING_MANAGER;

    /** HR roles see candidate data, so an administrator must approve them first. */
    public boolean requiresApproval() {
        return this != JOB_SEEKER;
    }
}
