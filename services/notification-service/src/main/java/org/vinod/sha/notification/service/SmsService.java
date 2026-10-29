package org.vinod.sha.notification.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.vinod.sha.notification.config.NotificationProperties;

@Slf4j
@Service
public class SmsService {

    private final NotificationProperties properties;

    public SmsService(NotificationProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (properties.getSms().isEnabled()
                && StringUtils.hasText(properties.getTwilio().getAccountSid())) {
            Twilio.init(properties.getTwilio().getAccountSid(),
                        properties.getTwilio().getAuthToken());
            log.info("[SmsService] Twilio initialised");
        } else {
            log.info("[SmsService] SMS disabled or Twilio credentials not configured");
        }
    }

    /**
     * Sends an SMS via Twilio.
     *
     * @param to   recipient phone number in E.164 format, e.g. +14155552671
     * @param body message text
     */
    public void sendSms(String to, String body) {
        if (!properties.getSms().isEnabled()) {
            log.info("[SmsService] SMS disabled - skipping to={}", to);
            return;
        }
        try {
            Message message = Message.creator(
                    new PhoneNumber(to),
                    new PhoneNumber(properties.getTwilio().getFromNumber()),
                    body
            ).create();
            log.info("[SmsService] SMS sent to={} sid={}", to, message.getSid());
        } catch (Exception e) {
            log.error("[SmsService] Failed to send SMS to={}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send SMS to " + to, e);
        }
    }
}
