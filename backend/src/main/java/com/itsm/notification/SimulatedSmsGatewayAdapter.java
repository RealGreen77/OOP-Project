package com.itsm.notification;

import com.itsm.domain.notification.DeliveryStatus;
import com.itsm.exception.ExternalServiceException;
import org.springframework.stereotype.Component;

/**
 * Adapts the vendor-style {@link ThirdPartySmsClient} API to the application's {@link SmsGateway} interface:
 * converts arguments to the vendor request object, truncates to SMS length and maps vendor status codes
 * to {@link DeliveryStatus} / exceptions.
 *
 * // [Pattern: Adapter] Classic object adapter (composition over the adaptee).
 */
@Component
public class SimulatedSmsGatewayAdapter implements SmsGateway {

    static final int MAX_SMS_LENGTH = 160;

    private final ThirdPartySmsClient client = new ThirdPartySmsClient("AC-ITSM-DEMO");

    @Override
    public DeliveryStatus sendSms(String phoneNumber, String text) {
        String body = text.length() > MAX_SMS_LENGTH ? text.substring(0, MAX_SMS_LENGTH - 3) + "..." : text;
        ThirdPartySmsClient.SmsResponse response = client.submit(
                new ThirdPartySmsClient.SmsRequest(client.accountSid(), phoneNumber.replace(" ", ""), body));
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return DeliveryStatus.SIMULATED;
        }
        throw new ExternalServiceException("SMS gateway", response.errorMessage(), null);
    }
}
