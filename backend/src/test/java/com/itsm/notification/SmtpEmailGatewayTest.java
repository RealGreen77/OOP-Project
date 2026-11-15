package com.itsm.notification;

import com.itsm.config.ItsmProperties;
import com.itsm.domain.notification.DeliveryStatus;
import com.itsm.exception.ExternalServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The SMTP adapter: real recipients are sent, demo-domain recipients are simulated, provider errors are translated. */
class SmtpEmailGatewayTest {

    private final List<SimpleMailMessage> sent = new ArrayList<>();

    private SmtpEmailGateway gateway(List<String> simulatedDomains, boolean failing) {
        JavaMailSender sender = (JavaMailSender) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{JavaMailSender.class}, (proxy, method, args) -> {
                    if (method.getName().equals("send") && args[0] instanceof SimpleMailMessage m) {
                        if (failing) throw new MailSendException("provider down");
                        sent.add(m);
                    }
                    return null;
                });
        var notifications = new ItsmProperties.Notifications("smtp", "from@example.org", true, simulatedDomains, "http://localhost");
        var props = new ItsmProperties(null, null, null, null, notifications, List.of(), false);
        return new SmtpEmailGateway(sender, props);
    }

    @Test
    @DisplayName("Recipients on a simulated domain are not sent; others are delivered")
    void simulatedDomainsAreSkipped() {
        SmtpEmailGateway gw = gateway(List.of("itsm.local"), false);
        assertThat(gw.send("lead@itsm.local", "s", "b")).isEqualTo(DeliveryStatus.SIMULATED);
        assertThat(gw.send("Admin@Sub.ITSM.local", "s", "b")).isEqualTo(DeliveryStatus.SIMULATED);
        assertThat(sent).isEmpty();
        assertThat(gw.send("real.person@gmail.com", "s", "b")).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getTo()).containsExactly("real.person@gmail.com");
    }

    @Test
    @DisplayName("With no simulated domains configured, everything is sent (Mailpit mode)")
    void noSimulatedDomainsSendsEverything() {
        SmtpEmailGateway gw = gateway(List.of(), false);
        assertThat(gw.send("lead@itsm.local", "s", "b")).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(sent).hasSize(1);
    }

    @Test
    @DisplayName("Provider failures are translated to ExternalServiceException")
    void providerFailureIsTranslated() {
        assertThatThrownBy(() -> gateway(List.of(), true).send("a@b.org", "s", "b"))
                .isInstanceOf(ExternalServiceException.class);
    }
}
