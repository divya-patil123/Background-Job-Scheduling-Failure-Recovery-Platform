package com.jobflow.service;

import com.jobflow.dto.AttemptResponse;
import com.jobflow.dto.ExecutionDetailResponse;
import com.jobflow.dto.ExecutionResponse;
import com.jobflow.entity.JobExecution;
import com.jobflow.enums.ExecutionStatus;
import com.jobflow.exception.NotFoundException;
import com.jobflow.repository.JobExecutionAttemptRepository;
import com.jobflow.repository.JobExecutionRepository;
import com.jobflow.util.DtoMapper;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionQueryService {

    private final JobExecutionRepository executionRepository;
    private final JobExecutionAttemptRepository attemptRepository;
    private final JobService jobService;
    private final DtoMapper mapper;

    public ExecutionQueryService(JobExecutionRepository executionRepository,
                                 JobExecutionAttemptRepository attemptRepository,
                                 JobService jobService, DtoMapper mapper) {
        this.executionRepository = executionRepository;
        this.attemptRepository = attemptRepository;
        this.jobService = jobService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ExecutionResponse findResponse(long id) {
        return mapper.toResponse(getExecution(id));
    }

    @Transactional(readOnly = true)
    public ExecutionDetailResponse findDetail(long id) {
        JobExecution execution = getExecution(id);
        List<AttemptResponse> attempts = attemptRepository
                .findByExecution_IdOrderByAttemptNumberAsc(id).stream().map(mapper::toResponse).toList();
        return new ExecutionDetailResponse(mapper.toResponse(execution), attempts);
    }

    @Transactional(readOnly = true)
    public Page<ExecutionResponse> findAll(Long jobId, ExecutionStatus status, Pageable pageable) {
        return executionRepository.search(jobId, status, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ExecutionResponse> findForJob(long jobId, Pageable pageable) {
        jobService.getJob(jobId);
        return executionRepository.findByJob_Id(jobId, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public boolean isQueued(long id) {
        return executionRepository.findById(id)
                .map(execution -> execution.getStatus() == ExecutionStatus.QUEUED)
                .orElse(false);
    }

    private JobExecution getExecution(long id) {
        return executionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Execution " + id + " was not found"));
    }
}
