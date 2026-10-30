package org.vinod.sha.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A page of 50 jobs is larger than WebClient's 256 KB default; the gateway's client must still read it. */
class WebClientConfigTest {

    private static final int ONE_MEGABYTE = 1024 * 1024;

    @Test
    void readsResponsesLargerThanWebClientsDefaultLimit() {
        DisposableServer server = largeResponseServer();
        try {
            String body = new WebClientConfig().webClientBuilder(DataSize.ofMegabytes(16)).build()
                    .get().uri("http://localhost:" + server.port() + "/jobs").retrieve().bodyToMono(String.class).block();

            assertEquals(ONE_MEGABYTE, body.length());
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void theLimitIsConfigurable() {
        DisposableServer server = largeResponseServer();
        try {
            Throwable e = assertThrows(RuntimeException.class, () -> new WebClientConfig().webClientBuilder(DataSize.ofKilobytes(256)).build()
                    .get().uri("http://localhost:" + server.port() + "/jobs").retrieve().bodyToMono(String.class).block());

            Throwable cause = e instanceof WebClientResponseException ? e.getCause() : e;
            assertInstanceOf(DataBufferLimitException.class, cause, String.valueOf(e));
        } finally {
            server.disposeNow();
        }
    }

    private static DisposableServer largeResponseServer() {
        String body = "x".repeat(ONE_MEGABYTE);
        return HttpServer.create().port(0)
                .route(routes -> routes.get("/jobs", (request, response) -> response.sendString(reactor.core.publisher.Mono.just(body))))
                .bindNow();
    }
}
