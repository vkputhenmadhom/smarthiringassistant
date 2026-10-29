package org.vinod.sha.auth.exception;

import org.vinod.sha.auth.entity.ApprovalStatus;

/** Raised when a PENDING or REJECTED account tries to obtain tokens. */
public class AccountNotApprovedException extends RuntimeException {

    private final ApprovalStatus status;

    public AccountNotApprovedException(ApprovalStatus status) {
        super(status == ApprovalStatus.REJECTED
                ? "Your account request was rejected by an administrator"
                : "Your account is awaiting administrator approval");
        this.status = status;
    }

    public ApprovalStatus getStatus() {
        return status;
    }
}
