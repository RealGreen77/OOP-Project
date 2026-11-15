package com.itsm.notification;

import com.itsm.domain.notification.DeliveryStatus;

/**
 * Target interface of the Adapter pattern for e-mail providers.
 * // [Pattern: Adapter] The application speaks this simple interface; adapters translate it to a concrete
 * //                   provider API (JavaMail/SMTP today; SendGrid, SES... tomorrow).
 */
public interface EmailGateway {

    DeliveryStatus send(String to, String subject, String body);
}
