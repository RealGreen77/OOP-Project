package com.itsm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background trigger for SLA monitoring. Kept separate from the transactional service so the
 * transaction proxy always applies; disabled in tests via {@code itsm.sla.scheduler-enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "itsm.sla", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class SlaMonitorScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitorScheduler.class);

    private final SlaMonitoringService monitoringService;

    public SlaMonitorScheduler(SlaMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @Scheduled(initialDelayString = "${itsm.sla.initial-delay-ms:15000}", fixedDelayString = "${itsm.sla.monitor-interval-ms:60000}")
    public void run() {
        try {
            monitoringService.scan();
        } catch (RuntimeException ex) {
            log.error("SLA monitoring run failed", ex);
        }
    }
}
