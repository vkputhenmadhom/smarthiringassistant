package org.vinod.sha.notification.service;

import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.vinod.sha.notification.config.NotificationProperties;

import jakarta.mail.internet.MimeMessage;
import java.util.Map;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final Configuration freeMarkerConfig;
    private final NotificationProperties properties;

    public EmailService(JavaMailSender mailSender,
                        Configuration freeMarkerConfig,
                        NotificationProperties properties) {
        this.mailSender = mailSender;
        this.freeMarkerConfig = freeMarkerConfig;
        this.properties = properties;
    }

    /**
     * Renders a FreeMarker template and sends it as an HTML email.
     *
     * @param to           recipient address
     * @param subject      email subject
     * @param templateName FreeMarker template filename (e.g. welcome.ftl)
     * @param model        template model variables
     * @return true if the email was sent, false if email delivery is disabled
     */
    public boolean sendTemplatedEmail(String to, String subject,
                                      String templateName, Map<String, Object> model) {
        if (!properties.getEmail().isEnabled()) {
            log.info("[EmailService] Email disabled - skipping to={} subject={}", to, subject);
            return false;
        }
        try {
            Template template = freeMarkerConfig.getTemplate(templateName);
            String body = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.getEmail().getFromAddress());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);

            mailSender.send(message);
            log.info("[EmailService] Email sent to={} subject={}", to, subject);
            return true;
        } catch (Exception e) {
            log.error("[EmailService] Failed to send email to={} subject={}: {}", to, subject, e.getMessage(), e);
            throw new RuntimeException("Failed to send email to " + to, e);
        }
    }

    /**
     * Sends a plain-text email without a template (for quick test messages).
     */
    public void sendPlainEmail(String to, String subject, String body) {
        if (!properties.getEmail().isEnabled()) {
            log.info("[EmailService] Email disabled - skipping to={}", to);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(properties.getEmail().getFromAddress());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            log.info("[EmailService] Plain email sent to={}", to);
        } catch (Exception e) {
            log.error("[EmailService] Failed to send plain email to={}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send plain email to " + to, e);
        }
    }
}
