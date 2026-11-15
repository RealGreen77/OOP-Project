package com.itsm.notification;

import com.itsm.domain.notification.DeliveryStatus;

/** Target interface of the Adapter pattern for SMS providers. // [Pattern: Adapter] */
public interface SmsGateway {

    DeliveryStatus sendSms(String phoneNumber, String text);
}
