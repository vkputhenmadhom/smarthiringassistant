package org.vinod.sha.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration properties bound from application.yaml under the {@code notification} prefix.
 */
@ConfigurationProperties(prefix = "notification")
@Data
public class NotificationProperties {

    private Email email = new Email();
    private Sms sms = new Sms();
    private Twilio twilio = new Twilio();

    @Data
    public static class Email {
        private boolean enabled = true;
        private String fromAddress = "noreply@smarthiringassistant.com";
    }

    @Data
    public static class Sms {
        private boolean enabled = false;
        private String provider = "twilio";
    }

    @Data
    public static class Twilio {
        private String accountSid = "";
        private String authToken = "";
        private String fromNumber = "";
    }
}

