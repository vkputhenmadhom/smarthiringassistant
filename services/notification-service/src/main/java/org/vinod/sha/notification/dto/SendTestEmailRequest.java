package org.vinod.sha.notification.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendTestEmailRequest {
    private String to;
    private String subject;
    private String body;
}
