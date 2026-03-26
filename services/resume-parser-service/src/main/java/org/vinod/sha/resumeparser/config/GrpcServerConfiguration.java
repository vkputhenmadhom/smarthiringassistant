package org.vinod.sha.resumeparser.config;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vinod.sha.resumeparser.grpc.ResumeParserGrpcService;

import java.io.IOException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class GrpcServerConfiguration {

    @Value("${grpc.server.port:9091}")
    private int grpcServerPort;

    private final ResumeParserGrpcService resumeParserGrpcService;

    @Bean
    public Server grpcServer() throws IOException {
        Server server = ServerBuilder
                .forPort(grpcServerPort)
                .addService(resumeParserGrpcService)
                .build()
                .start();

        log.info("gRPC Server started on port: {}", grpcServerPort);

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down gRPC server");
            try {
                server.shutdown();
                log.info("gRPC server shut down successfully");
            } catch (Exception e) {
                log.error("Error shutting down gRPC server", e);
            }
        }));

        return server;
    }
}

