package org.vinod.sha.serverless.job;

import java.util.List;

public record JobAnalyzeRequest(
        String jobId,
        String title,
        String description,
        List<String> requiredSkills,
        String location,
        String employmentType
) {
    public boolean isValid() {
        return title != null && !title.isBlank()
                && description != null && !description.isBlank();
    }
}

