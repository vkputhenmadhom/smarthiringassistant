package org.vinod.sha.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vinod.sha.notification.entity.InAppNotification;
import org.vinod.sha.notification.service.InAppNotificationService;

import java.util.List;
import java.util.Map;

/**
 * In-app notifications, for the gateway. Internal (no published port): the gateway passes the signed-in
 * user's id, so a user only ever reads and marks their own.
 */
@RestController
@RequestMapping("in-app")
public class InAppNotificationController {

    private final InAppNotificationService service;

    public InAppNotificationController(InAppNotificationService service) {
        this.service = service;
    }

    @GetMapping("users/{userId}")
    public List<InAppNotification> forUser(@PathVariable Long userId,
                                           @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return service.forUser(userId, unreadOnly);
    }

    @PostMapping("users/{userId}/{id}/read")
    public ResponseEntity<InAppNotification> markRead(@PathVariable Long userId, @PathVariable Long id) {
        return service.markRead(id, userId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("users/{userId}/read-all")
    public Map<String, Integer> markAllRead(@PathVariable Long userId) {
        return Map.of("updated", service.markAllRead(userId));
    }
}
