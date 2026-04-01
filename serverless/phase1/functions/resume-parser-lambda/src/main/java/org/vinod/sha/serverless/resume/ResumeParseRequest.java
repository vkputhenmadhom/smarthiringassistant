package org.vinod.sha.serverless.resume;

public record ResumeParseRequest(
        String candidateId,
        String fileName,
        String contentType,
        String content
) {
}

