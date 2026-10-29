package org.vinod.sha.matcher.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchUpdateRequest {
    private String status; // a MatchStatus: PENDING, MATCHED, SHORTLISTED, REJECTED, HIRED
    private String comments;
}

