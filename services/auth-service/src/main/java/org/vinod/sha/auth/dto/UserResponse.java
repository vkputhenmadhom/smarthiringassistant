package org.vinod.sha.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.vinod.sha.auth.entity.User;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private String approvalStatus;
    private LocalDateTime createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .approvalStatus(user.getApprovalStatus().name())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
