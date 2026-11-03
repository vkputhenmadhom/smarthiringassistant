package org.vinod.sha.gateway.graphql;

import graphql.ErrorClassification;

/** A backend outcome to show the client as-is (type and message), instead of a generic INTERNAL_ERROR. */
public class ClientFacingException extends RuntimeException {

    private final ErrorClassification classification;

    public ClientFacingException(ErrorClassification classification, String message) {
        super(message);
        this.classification = classification;
    }

    public ErrorClassification getClassification() {
        return classification;
    }
}
