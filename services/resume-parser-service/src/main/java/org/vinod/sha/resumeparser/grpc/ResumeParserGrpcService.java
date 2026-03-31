package org.vinod.sha.resumeparser.grpc;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.vinod.sha.resumeparser.entity.Resume;
import org.vinod.sha.resumeparser.entity.ParseStatus;
import org.vinod.sha.resumeparser.service.ResumeParserService;
import org.vinod.smarthiringassistant.grpc.resume.*;

@Slf4j
@Service
public class ResumeParserGrpcService extends ResumeParserServiceGrpc.ResumeParserServiceImplBase {

    private final ResumeParserService resumeParserService;

    public ResumeParserGrpcService(ResumeParserService resumeParserService) {
        this.resumeParserService = resumeParserService;
    }

    @Override
    public void parseResume(ParseResumeRequest request, StreamObserver<ParseResumeResponse> responseObserver) {
        log.info("Received gRPC ParseResume request for file: {}", request.getFileName());

        try {
            long startTime = System.currentTimeMillis();

            // Parse the resume
            Resume resume = resumeParserService.parseResume(
                    1L, // TODO: Extract from request
                    request.getFileName(),
                    request.getFileContent().toByteArray()
            );

            long processingTime = System.currentTimeMillis() - startTime;

            // Build response
            ParsedResume parsedResume = buildParsedResumeProto(resume);
            
            ParseResumeResponse response = ParseResumeResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Resume parsed successfully")
                    .setParsedResume(parsedResume)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("Successfully processed ParseResume request for resume: {}", resume.getResumeId());

        } catch (Exception e) {
            log.error("Error processing ParseResume request", e);
            
            ParseResumeResponse errorResponse = ParseResumeResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error: " + e.getMessage())
                    .build();

            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void validateResume(ParseResumeRequest request, StreamObserver<ParseResumeResponse> responseObserver) {
        log.info("Received gRPC ValidateResume request for file: {}", request.getFileName());

        try {
            ParseResumeResponse response = ParseResumeResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Resume is valid")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error validating resume", e);
            responseObserver.onError(e);
        }
    }

    private ParsedResume buildParsedResumeProto(Resume resume) {
        ParsedResume.Builder builder = ParsedResume.newBuilder();

        if (resume.getParsedData() != null) {
            var data = resume.getParsedData();
            builder.setFullName(data.getFullName() != null ? data.getFullName() : "")
                    .setEmail(data.getEmail() != null ? data.getEmail() : "")
                    .setPhone(data.getPhone() != null ? data.getPhone() : "")
                    .setLocation(data.getLocation() != null ? data.getLocation() : "")
                    .setSummary(data.getSummary() != null ? data.getSummary() : "");

            if (data.getSkills() != null) {
                builder.addAllSkills(data.getSkills());
            }

            if (data.getCertifications() != null) {
                builder.addAllCertifications(data.getCertifications());
            }
        }

        return builder.build();
    }
}

