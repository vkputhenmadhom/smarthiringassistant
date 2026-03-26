package org.vinod.sha.resumeparser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeParseResponse {
    private String resumeId;
    private String fileName;
    private String status;
    private String message;
}

