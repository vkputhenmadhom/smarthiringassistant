package org.vinod.sha.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    /**
     * How long a backend's address is reused. Reactor Netty otherwise keeps it for the DNS answer's TTL (600 s
     * from Docker's DNS), so a backend container that is recreated, and gets a new address, is refused for up
     * to ten minutes ("Connection refused: resume-parser-service/172.18.0.13").
     */
    static final Duration DNS_CACHE = Duration.ofSeconds(10);

    static HttpClient withShortDnsCache(HttpClient client) {
        return client.resolver(spec -> spec.cacheMaxTimeToLive(DNS_CACHE));
    }

    /** The REST routes (/api/resumes/**, /api/jobs/**, ...) */
    @Bean
    public HttpClientCustomizer shortDnsCache() {
        return WebClientConfig::withShortDnsCache;
    }

    /**
     * The resolvers' backend client. This bean replaces Boot's auto-configured builder, so spring.codec.* does
     * not reach it; the response limit is set here instead. WebClient's default of 256 KB is less than a page of
     * 50 jobs (descriptions are long), which failed the read and showed HR an empty jobs list.
     */
    @Bean
    public WebClient.Builder webClientBuilder(@Value("${gateway.backend.max-response-size:16MB}") DataSize maxResponseSize) {
        int maxBytes = (int) Math.min(Integer.MAX_VALUE, maxResponseSize.toBytes());
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(withShortDnsCache(HttpClient.create())))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxBytes));
    }
}
