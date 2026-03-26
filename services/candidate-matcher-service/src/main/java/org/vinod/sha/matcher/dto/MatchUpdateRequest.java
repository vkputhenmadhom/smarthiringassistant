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
    private String status; // NEW, REVIEWED, ACCEPTED, REJECTED
    private String comments;
}

