package org.vinod.sha.resumeparser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeStatusResponse {
    private String resumeId;
    private String fileName;
    private String status;
    private String createdAt;
    private String parsedAt;
    private String message;
}

