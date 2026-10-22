package com.jobflow.service;

import com.jobflow.dto.AlertResponse;
import com.jobflow.entity.AdminAlert;
import com.jobflow.exception.NotFoundException;
import com.jobflow.repository.AdminAlertRepository;
import com.jobflow.util.DtoMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {

    private final AdminAlertRepository alertRepository;
    private final DtoMapper mapper;

    public AlertService(AdminAlertRepository alertRepository, DtoMapper mapper) {
        this.alertRepository = alertRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<AlertResponse> findAll(Pageable pageable) {
        return alertRepository.findAll(pageable).map(mapper::toResponse);
    }

    @Transactional
    public AlertResponse acknowledge(long id) {
        AdminAlert alert = alertRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Alert " + id + " was not found"));
        alert.acknowledge();
        return mapper.toResponse(alert);
    }
}
