package com.itsm.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stand-in for a vendor SMS SDK (Twilio-style) with its own, incompatible API: request/response objects,
 * an account SID and numeric status codes. It deliberately does NOT implement {@link SmsGateway};
 * {@link SimulatedSmsGatewayAdapter} adapts it. Swapping in a real vendor SDK only touches the adapter.
 */
public class ThirdPartySmsClient {

    private static final Logger log = LoggerFactory.getLogger(ThirdPartySmsClient.class);

    public record SmsRequest(String accountSid, String toE164, String messageBody) {}

    public record SmsResponse(int statusCode, String messageSid, String errorMessage) {}

    private final String accountSid;
    private final AtomicLong sent = new AtomicLong();

    public ThirdPartySmsClient(String accountSid) {
        this.accountSid = accountSid;
    }

    public String accountSid() {
        return accountSid;
    }

    public SmsResponse submit(SmsRequest request) {
        if (request.toE164() == null || !request.toE164().matches("\\+?[0-9]{8,15}")) {
            return new SmsResponse(400, null, "Invalid destination number");
        }
        String sid = "SM" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[SIMULATED SMS #{}] to={} sid={} body=\"{}\"", sent.incrementAndGet(), request.toE164(), sid,
                request.messageBody());
        return new SmsResponse(201, sid, null);
    }
}
