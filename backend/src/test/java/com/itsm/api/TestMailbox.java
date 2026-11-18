package com.itsm.api;

import com.itsm.domain.notification.DeliveryStatus;
import com.itsm.notification.EmailGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Test double for the e-mail adapter: records every message so tests can read verification codes. */
public class TestMailbox implements EmailGateway {

    public record Mail(String to, String subject, String body) {}

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");
    private final Map<String, List<Mail>> byRecipient = new ConcurrentHashMap<>();

    @Override
    public DeliveryStatus send(String to, String subject, String body) {
        byRecipient.computeIfAbsent(to.toLowerCase(), k -> new CopyOnWriteArrayList<>()).add(new Mail(to, subject, body));
        return DeliveryStatus.SIMULATED;
    }

    public List<Mail> inbox(String email) {
        return byRecipient.getOrDefault(email.toLowerCase(), List.of());
    }

    /** The token from the most recent password-reset e-mail to this address. */
    public String latestResetToken(String email) {
        Pattern token = Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)");
        List<Mail> mails = inbox(email);
        for (int i = mails.size() - 1; i >= 0; i--) {
            Matcher m = token.matcher(mails.get(i).body());
            if (m.find()) return m.group(1);
        }
        throw new IllegalStateException("No reset e-mail for " + email);
    }

    /** The 6-digit code from the most recent verification e-mail to this address. */
    public String latestCode(String email) {
        List<Mail> mails = inbox(email);
        for (int i = mails.size() - 1; i >= 0; i--) {
            Matcher m = CODE.matcher(mails.get(i).body());
            if (mails.get(i).subject().contains("verification code") && m.find()) return m.group(1);
        }
        throw new IllegalStateException("No verification e-mail for " + email);
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        public TestMailbox testMailbox() {
            return new TestMailbox();
        }
    }
}
