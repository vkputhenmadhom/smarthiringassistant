package org.vinod.sha.resumeparser.security;

import java.security.Principal;

/** The caller identified by a verified JWT: auth-service's numeric user id plus username. */
public record AuthenticatedUser(Long id, String username) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
