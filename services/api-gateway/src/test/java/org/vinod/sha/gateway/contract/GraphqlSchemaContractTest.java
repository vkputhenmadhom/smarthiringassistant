package org.vinod.sha.gateway.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphqlSchemaContractTest {

    @Test
    void gatewaySchema_matchesCanonicalContractSchema() throws Exception {
        Path serviceSchemaPath = Path.of("src", "main", "resources", "graphql", "schema.graphqls");
        Path contractSchemaPath = Path.of("..", "..", "contracts", "graphql", "gateway", "schema.graphqls").normalize();

        String serviceSchema = Files.readString(serviceSchemaPath)
                .replace("\r\n", "\n")
                .trim();
        String contractSchema = Files.readString(contractSchemaPath)
                .replace("\r\n", "\n")
                .trim();

        assertEquals(contractSchema, serviceSchema,
                "Gateway GraphQL schema drifted from contracts/graphql/gateway/schema.graphqls");
    }
}

