package org.vinod.sha.serverless.job;

import java.util.List;

public record JobAnalyzeResponse(
        String jobId,
        String seniorityLevel,
        List<String> extractedSkills,
        String workMode,
        String summary,
        String analysisStatus
) {
}

