package org.vinod.sha.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 64, message = "Username must be between 3 and 64 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 64, message = "First name must be between 2 and 64 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 64, message = "Last name must be between 2 and 64 characters")
    private String lastName;

    @Pattern(regexp = "^(ADMIN|RECRUITER|JOB_SEEKER|HIRING_MANAGER)?$", message = "Role must be ADMIN, RECRUITER, JOB_SEEKER, or HIRING_MANAGER")
    private String role; // RECRUITER, JOB_SEEKER, HIRING_MANAGER
}

