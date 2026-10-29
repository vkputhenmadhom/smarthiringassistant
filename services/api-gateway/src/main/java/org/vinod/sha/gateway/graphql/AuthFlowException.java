package org.vinod.sha.gateway.graphql;

import graphql.ErrorClassification;

/** An auth-service outcome to show the client as-is, e.g. a wrong password or a pending account. */
public class AuthFlowException extends RuntimeException {

    /** Registration succeeded but the account needs an administrator's approval before login. */
    public static final ErrorClassification PENDING_APPROVAL = ErrorClassification.errorClassification("PENDING_APPROVAL");

    private final ErrorClassification classification;

    public AuthFlowException(ErrorClassification classification, String message) {
        super(message);
        this.classification = classification;
    }

    public ErrorClassification getClassification() {
        return classification;
    }
}
