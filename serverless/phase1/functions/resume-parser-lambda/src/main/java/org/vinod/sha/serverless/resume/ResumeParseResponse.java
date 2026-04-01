package org.vinod.sha.serverless.resume;

import java.util.List;

public record ResumeParseResponse(
        String candidateId,
        String email,
        int experienceYears,
        List<String> skills,
        String parseStatus
) {
}

