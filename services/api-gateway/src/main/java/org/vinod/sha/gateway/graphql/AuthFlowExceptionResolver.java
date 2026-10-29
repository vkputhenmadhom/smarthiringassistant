package org.vinod.sha.gateway.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;

/** Surfaces {@link AuthFlowException} messages instead of a generic INTERNAL_ERROR. */
@Component
public class AuthFlowExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof AuthFlowException authFlow) {
            return GraphqlErrorBuilder.newError(env)
                    .errorType(authFlow.getClassification())
                    .message(authFlow.getMessage())
                    .build();
        }
        return null;
    }
}
