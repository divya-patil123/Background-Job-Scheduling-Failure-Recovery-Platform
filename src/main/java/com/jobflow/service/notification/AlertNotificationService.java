package com.jobflow.service.notification;

import com.jobflow.entity.AdminAlert;

public interface AlertNotificationService {
    void notifyAdministrator(AdminAlert alert);
}
