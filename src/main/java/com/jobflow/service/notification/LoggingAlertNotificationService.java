package com.jobflow.service.notification;

import com.jobflow.entity.AdminAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoggingAlertNotificationService implements AlertNotificationService {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlertNotificationService.class);

    @Override
    public void notifyAdministrator(AdminAlert alert) {
        log.warn("Administrator alert created: alertId={} jobId={} failureCount={}",
                alert.getId(), alert.getJob().getId(), alert.getFailureCount());
    }
}
