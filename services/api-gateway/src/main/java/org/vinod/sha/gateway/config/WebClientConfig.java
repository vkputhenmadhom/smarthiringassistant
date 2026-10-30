package org.vinod.sha.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * The resolvers' backend client. This bean replaces Boot's auto-configured builder, so spring.codec.* does
     * not reach it; the response limit is set here instead. WebClient's default of 256 KB is less than a page of
     * 50 jobs (descriptions are long), which failed the read and showed HR an empty jobs list.
     */
    @Bean
    public WebClient.Builder webClientBuilder(@Value("${gateway.backend.max-response-size:16MB}") DataSize maxResponseSize) {
        int maxBytes = (int) Math.min(Integer.MAX_VALUE, maxResponseSize.toBytes());
        return WebClient.builder().codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxBytes));
    }
}
