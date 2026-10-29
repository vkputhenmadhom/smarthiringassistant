package org.vinod.sha.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vinod.sha.auth.dto.UserResponse;
import org.vinod.sha.auth.entity.ApprovalStatus;
import org.vinod.sha.auth.entity.User;
import org.vinod.sha.auth.entity.UserRole;
import org.vinod.sha.auth.repository.UserRepository;

import java.util.List;
import java.util.NoSuchElementException;

/** Administrator decisions on HR accounts awaiting approval. */
@Service
@Transactional
@Slf4j
public class UserApprovalService {

    private final UserRepository userRepository;

    public UserApprovalService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findByStatus(ApprovalStatus status) {
        return userRepository.findByApprovalStatusOrderByCreatedAtAsc(status).stream()
                .map(UserResponse::from)
                .toList();
    }

    /**
     * Approves an account, optionally changing its role. The role must be an HR role:
     * candidates never need approval, so approving one as JOB_SEEKER is a mistake.
     */
    public UserResponse approve(Long userId, UserRole role, String approvedBy) {
        User user = findUser(userId);
        if (role != null) {
            if (!role.requiresApproval()) {
                throw new IllegalArgumentException("Only HR roles can be approved");
            }
            user.setRole(role);
        }
        user.setApprovalStatus(ApprovalStatus.APPROVED);
        log.info("Account {} approved as {} by {}", user.getUsername(), user.getRole(), approvedBy);
        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse reject(Long userId, String rejectedBy) {
        User user = findUser(userId);
        if (user.getUsername().equals(rejectedBy)) {
            throw new IllegalArgumentException("Administrators cannot reject their own account");
        }
        user.setApprovalStatus(ApprovalStatus.REJECTED);
        log.info("Account {} rejected by {}", user.getUsername(), rejectedBy);
        return UserResponse.from(userRepository.save(user));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
    }
}
