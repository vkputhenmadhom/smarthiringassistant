package org.vinod.sha.matcher.entity;

/**
 * A match's place in the hiring pipeline. Mirrors the GraphQL {@code MatchStatus} the HR dashboard uses, so
 * the gateway passes it through unchanged. Rows written before this enum (NEW, REVIEWED, ACCEPTED) are
 * migrated by schema-candidate-matcher.sql.
 */
public enum MatchStatus {
    /** Not scored yet. */
    PENDING,
    /** Scored above the threshold; the starting point for every match the matcher creates. */
    MATCHED,
    SHORTLISTED,
    REJECTED,
    HIRED
}
