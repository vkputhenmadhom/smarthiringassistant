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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

@Configuration
public class GraphQlScalarConfig {

    /**
     * Backend values for a DateTime field. Several services send {@code LocalDateTime} (no offset, e.g.
     * screening-bot's {@code "2026-04-05T03:51:32.892"}); every service runs in UTC, so those are read as UTC
     * rather than failing the whole query.
     */
    static String serializeDateTime(Object value) {
        if (value instanceof OffsetDateTime odt) {
            return odt.toString();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.atOffset(ZoneOffset.UTC).toString();
        }
        if (value instanceof Instant instant) {
            return instant.atOffset(ZoneOffset.UTC).toString();
        }
        if (value instanceof String s) {
            try {
                return OffsetDateTime.parse(s).toString();
            } catch (DateTimeParseException withoutOffset) {
                try {
                    return LocalDateTime.parse(s).atOffset(ZoneOffset.UTC).toString();
                } catch (DateTimeParseException ex) {
                    throw new CoercingSerializeException("Invalid DateTime format, expected ISO-8601: " + s, ex);
                }
            }
        }
        throw new CoercingSerializeException("Expected DateTime as OffsetDateTime, LocalDateTime or ISO-8601 string");
    }

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        GraphQLScalarType dateTimeScalar = GraphQLScalarType.newScalar()
                .name("DateTime")
                .description("ISO-8601 date-time scalar")
                .coercing(new Coercing<OffsetDateTime, String>() {
                    @Override
                    public String serialize(Object dataFetcherResult) {
                        return serializeDateTime(dataFetcherResult);
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

