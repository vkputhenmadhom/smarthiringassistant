package org.vinod.sha.auth.service;

import org.junit.jupiter.api.Test;
import org.vinod.sha.auth.dto.UserResponse;
import org.vinod.sha.auth.entity.ApprovalStatus;
import org.vinod.sha.auth.entity.User;
import org.vinod.sha.auth.entity.UserRole;
import org.vinod.sha.auth.repository.UserRepository;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserApprovalServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserApprovalService approvalService = new UserApprovalService(userRepository);

    @Test
    void approvesPendingAccountKeepingRequestedRole() {
        givenUser(UserRole.RECRUITER);

        UserResponse response = approvalService.approve(7L, null, "admin");

        assertEquals("APPROVED", response.getApprovalStatus());
        assertEquals("RECRUITER", response.getRole());
    }

    @Test
    void approvalCanAssignADifferentHrRole() {
        givenUser(UserRole.ADMIN);

        UserResponse response = approvalService.approve(7L, UserRole.HIRING_MANAGER, "admin");

        assertEquals("HIRING_MANAGER", response.getRole());
    }

    @Test
    void refusesToApproveAsCandidate() {
        givenUser(UserRole.RECRUITER);

        assertThrows(IllegalArgumentException.class, () -> approvalService.approve(7L, UserRole.JOB_SEEKER, "admin"));
    }

    @Test
    void rejectsAccount() {
        givenUser(UserRole.RECRUITER);

        assertEquals("REJECTED", approvalService.reject(7L, "admin").getApprovalStatus());
    }

    @Test
    void adminCannotRejectOwnAccount() {
        givenUser(UserRole.ADMIN);

        assertThrows(IllegalArgumentException.class, () -> approvalService.reject(7L, "pat"));
    }

    @Test
    void unknownUserIsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> approvalService.approve(99L, null, "admin"));
    }

    private void givenUser(UserRole role) {
        User user = User.builder()
                .id(7L).username("pat").email("pat@example.com").password("hashed")
                .firstName("Pat").lastName("Lee")
                .role(role).approvalStatus(ApprovalStatus.PENDING)
                .build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
