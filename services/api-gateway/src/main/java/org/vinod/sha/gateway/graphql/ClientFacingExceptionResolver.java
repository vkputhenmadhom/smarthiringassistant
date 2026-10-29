package org.vinod.sha.gateway.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;

/** Surfaces {@link ClientFacingException} messages (auth outcomes, job write failures) instead of a generic INTERNAL_ERROR. */
@Component
public class ClientFacingExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof ClientFacingException clientFacing) {
            return GraphqlErrorBuilder.newError(env)
                    .errorType(clientFacing.getClassification())
                    .message(clientFacing.getMessage())
                    .build();
        }
        return null;
    }
}
