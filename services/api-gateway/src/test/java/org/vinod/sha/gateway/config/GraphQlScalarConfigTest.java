package org.vinod.sha.gateway.config;

import graphql.schema.CoercingSerializeException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphQlScalarConfigTest {

    @Test
    void offsetTimestampsPassThrough() {
        assertEquals("2026-04-05T03:51:32.892+05:30", GraphQlScalarConfig.serializeDateTime("2026-04-05T03:51:32.892+05:30"));
    }

    @Test
    void timestampsWithoutAnOffsetAreReadAsUtc() {
        // screening-bot's createdAt, which used to fail the whole screeningSession query
        assertEquals("2026-04-05T03:51:32.892Z", GraphQlScalarConfig.serializeDateTime("2026-04-05T03:51:32.892"));
        assertEquals("2026-04-05T03:51:32Z", GraphQlScalarConfig.serializeDateTime(LocalDateTime.of(2026, 4, 5, 3, 51, 32)));
        assertEquals("2026-04-05T03:51:32Z", GraphQlScalarConfig.serializeDateTime(
                OffsetDateTime.of(2026, 4, 5, 3, 51, 32, 0, ZoneOffset.UTC)));
    }

    @Test
    void nonDatesStillFail() {
        assertThrows(CoercingSerializeException.class, () -> GraphQlScalarConfig.serializeDateTime("yesterday"));
    }
}
