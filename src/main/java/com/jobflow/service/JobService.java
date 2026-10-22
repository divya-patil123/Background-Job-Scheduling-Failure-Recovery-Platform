package com.jobflow.service;

import com.jobflow.dto.JobResponse;
import com.jobflow.dto.JobUpsertRequest;
import com.jobflow.entity.ScheduledJob;
import com.jobflow.exception.ConflictException;
import com.jobflow.exception.InvalidRequestException;
import com.jobflow.exception.NotFoundException;
import com.jobflow.repository.JobExecutionRepository;
import com.jobflow.repository.ScheduledJobRepository;
import com.jobflow.service.executor.TaskHandlerRegistry;
import com.jobflow.util.DtoMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.support.CronExpression;

@Service
public class JobService {

    private final ScheduledJobRepository jobRepository;
    private final JobExecutionRepository executionRepository;
    private final TaskHandlerRegistry handlers;
    private final SchedulingCalculator schedulingCalculator;
    private final DtoMapper mapper;

    public JobService(ScheduledJobRepository jobRepository, JobExecutionRepository executionRepository,
                      TaskHandlerRegistry handlers, SchedulingCalculator schedulingCalculator, DtoMapper mapper) {
        this.jobRepository = jobRepository;
        this.executionRepository = executionRepository;
        this.handlers = handlers;
        this.schedulingCalculator = schedulingCalculator;
        this.mapper = mapper;
    }

    @Transactional
    public JobResponse create(JobUpsertRequest request) {
        validateTaskConfiguration(request, 1000L, 60000L);
        Instant now = Instant.now();
        Instant firstRun = schedulingCalculator.nextRun(request.scheduleType(), request.intervalSeconds(),
                request.cronExpression(), now);
        ScheduledJob job = new ScheduledJob(request.name().trim(), request.description(), request.taskType(),
                request.scheduleType(), request.intervalSeconds(), request.cronExpression(),
                request.configuration(), request.enabled() == null || request.enabled(),
                valueOrDefault(request.maxRetries(), 3),
                valueOrDefault(request.initialRetryDelayMs(), 1000L),
                valueOrDefault(request.maxRetryDelayMs(), 60000L),
                valueOrDefault(request.failureThreshold(), 3), firstRun);
        return mapper.toResponse(jobRepository.save(job));
    }

    @Transactional(readOnly = true)
    public List<JobResponse> findAll() {
        return jobRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public JobResponse findById(long id) {
        return mapper.toResponse(getJob(id));
    }

    @Transactional
    public JobResponse update(long id, JobUpsertRequest request) {
        ScheduledJob job = getJob(id);
        if (job.isExecutionLocked()) {
            throw new ConflictException("A running or retrying job cannot be updated");
        }
        validateTaskConfiguration(request, job.getInitialRetryDelayMs(), job.getMaxRetryDelayMs());
        Instant now = Instant.now();
        Instant nextRun = schedulingCalculator.nextRun(request.scheduleType(), request.intervalSeconds(),
                request.cronExpression(), now);
        job.update(request.name().trim(), request.description(), request.taskType(), request.scheduleType(),
                request.intervalSeconds(), request.cronExpression(), request.configuration(),
                valueOrDefault(request.maxRetries(), job.getMaxRetries()),
                valueOrDefault(request.initialRetryDelayMs(), job.getInitialRetryDelayMs()),
                valueOrDefault(request.maxRetryDelayMs(), job.getMaxRetryDelayMs()),
                valueOrDefault(request.failureThreshold(), job.getFailureThreshold()), nextRun);
        if (request.enabled() != null) {
            job.setEnabled(request.enabled(), request.enabled() ? nextRun : job.getNextRunAt());
        }
        return mapper.toResponse(job);
    }

    @Transactional
    public JobResponse setEnabled(long id, boolean enabled) {
        ScheduledJob job = getJob(id);
        if (job.isExecutionLocked()) {
            throw new ConflictException("A running or retrying job cannot be enabled or disabled");
        }
        Instant nextRun = enabled
                ? schedulingCalculator.nextRun(job.getScheduleType(), job.getIntervalSeconds(),
                        job.getCronExpression(), Instant.now())
                : job.getNextRunAt();
        job.setEnabled(enabled, nextRun);
        return mapper.toResponse(job);
    }

    @Transactional
    public void delete(long id) {
        ScheduledJob job = getJob(id);
        if (job.isExecutionLocked()) {
            throw new ConflictException("A running or retrying job cannot be deleted");
        }
        if (executionRepository.existsByJob_Id(id)) {
            throw new ConflictException("A job with execution history cannot be deleted");
        }
        jobRepository.delete(job);
    }

    @Transactional(readOnly = true)
    public ScheduledJob getJob(long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Job " + id + " was not found"));
    }

    private void validateTaskConfiguration(JobUpsertRequest request, long defaultInitialDelay,
                                           long defaultMaxDelay) {
        long initialDelay = valueOrDefault(request.initialRetryDelayMs(), defaultInitialDelay);
        long maxDelay = valueOrDefault(request.maxRetryDelayMs(), defaultMaxDelay);
        if (maxDelay < initialDelay) {
            throw new InvalidRequestException("maxRetryDelayMs must be greater than or equal to initialRetryDelayMs");
        }
        try {
            handlers.validate(request.taskType(), request.configuration());
            if (request.scheduleType() == com.jobflow.enums.ScheduleType.CRON) {
                CronExpression.parse(request.cronExpression());
            }
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(exception.getMessage());
        }
        if (request.configuration().size() > 50) {
            throw new InvalidRequestException("Configuration may contain at most 50 properties");
        }
        if (request.maxRetries() != null && request.maxRetries() > 20) {
            throw new InvalidRequestException("maxRetries cannot exceed 20");
        }
    }

    private static <T> T valueOrDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }
}
