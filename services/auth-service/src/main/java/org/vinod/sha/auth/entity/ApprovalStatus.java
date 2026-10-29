package org.vinod.sha.auth.entity;

/**
 * Lifecycle of an account that needs an administrator's sign-off before it can log in.
 * Candidate (JOB_SEEKER) accounts are approved on creation; HR accounts start PENDING.
 */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
