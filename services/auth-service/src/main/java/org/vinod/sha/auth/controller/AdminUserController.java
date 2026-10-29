package org.vinod.sha.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.vinod.sha.auth.dto.UserResponse;
import org.vinod.sha.auth.entity.ApprovalStatus;
import org.vinod.sha.auth.entity.UserRole;
import org.vinod.sha.auth.service.UserApprovalService;

import java.util.List;
import java.util.NoSuchElementException;

/** ADMIN-only account approval endpoints (secured in SecurityConfiguration). */
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserApprovalService approvalService;

    public AdminUserController(UserApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    public List<UserResponse> list(@RequestParam(defaultValue = "PENDING") ApprovalStatus status) {
        return approvalService.findByStatus(status);
    }

    @PostMapping("{id}/approve")
    public UserResponse approve(@PathVariable Long id,
                                @RequestParam(required = false) UserRole role,
                                Authentication authentication) {
        return approvalService.approve(id, role, authentication.getName());
    }

    @PostMapping("{id}/reject")
    public UserResponse reject(@PathVariable Long id, Authentication authentication) {
        return approvalService.reject(id, authentication.getName());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
