package org.vinod.sha.gateway.config;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@Configuration
public class GraphQlScalarConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        GraphQLScalarType dateTimeScalar = GraphQLScalarType.newScalar()
                .name("DateTime")
                .description("ISO-8601 date-time scalar")
                .coercing(new Coercing<OffsetDateTime, String>() {
                    @Override
                    public String serialize(Object dataFetcherResult) {
                        if (dataFetcherResult instanceof OffsetDateTime odt) {
                            return odt.toString();
                        }
                        if (dataFetcherResult instanceof String s) {
                            return parseDateTime(s).toString();
                        }
                        throw new CoercingSerializeException("Expected DateTime as OffsetDateTime or ISO-8601 string");
                    }

                    @Override
                    public OffsetDateTime parseValue(Object input) {
                        if (input instanceof String s) {
                            return parseDateTime(s);
                        }
                        throw new CoercingParseValueException("Expected DateTime input as ISO-8601 string");
                    }

                    @Override
                    public OffsetDateTime parseLiteral(Object input) {
                        if (input instanceof StringValue sv) {
                            return parseDateTime(sv.getValue());
                        }
                        throw new CoercingParseLiteralException("Expected DateTime literal as string");
                    }

                    private OffsetDateTime parseDateTime(String value) {
                        try {
                            return OffsetDateTime.parse(value);
                        } catch (DateTimeParseException ex) {
                            throw new CoercingParseValueException("Invalid DateTime format, expected ISO-8601", ex);
                        }
                    }
                })
                .build();

        GraphQLScalarType uploadScalar = GraphQLScalarType.newScalar()
                .name("Upload")
                .description("Upload scalar mapped as generic object placeholder")
                .coercing(new Coercing<Object, Object>() {
                    @Override
                    public Object serialize(Object dataFetcherResult) {
                        return dataFetcherResult;
                    }

                    @Override
                    public Object parseValue(Object input) {
                        return input;
                    }

                    @Override
                    public Object parseLiteral(Object input) {
                        return input;
                    }
                })
                .build();

        return wiringBuilder -> wiringBuilder
                .scalar(dateTimeScalar)
                .scalar(uploadScalar);
    }
}

