package com.jobflow.service;

import com.jobflow.entity.JobExecution;
import com.jobflow.entity.ScheduledJob;
import com.jobflow.enums.TriggerType;
import com.jobflow.exception.ConflictException;
import com.jobflow.exception.NotFoundException;
import com.jobflow.repository.JobExecutionRepository;
import com.jobflow.repository.ScheduledJobRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionReservationService {

    private final ScheduledJobRepository jobRepository;
    private final JobExecutionRepository executionRepository;
    private final SchedulingCalculator schedulingCalculator;

    public ExecutionReservationService(ScheduledJobRepository jobRepository,
                                       JobExecutionRepository executionRepository,
                                       SchedulingCalculator schedulingCalculator) {
        this.jobRepository = jobRepository;
        this.executionRepository = executionRepository;
        this.schedulingCalculator = schedulingCalculator;
    }

    @Transactional
    public Long reserveScheduled(long jobId) {
        Instant now = Instant.now();
        ScheduledJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return null;
        }
        Instant nextRunAt = schedulingCalculator.nextRun(job.getScheduleType(), job.getIntervalSeconds(),
                job.getCronExpression(), now);
        if (jobRepository.claimScheduled(jobId, now, nextRunAt) == 0) {
            return null;
        }
        ScheduledJob claimedJob = jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job " + jobId + " was not found"));
        return executionRepository.save(new JobExecution(claimedJob, TriggerType.SCHEDULED)).getId();
    }

    @Transactional
    public long reserveManual(long jobId) {
        Instant now = Instant.now();
        if (jobRepository.findById(jobId).isEmpty()) {
            throw new NotFoundException("Job " + jobId + " was not found");
        }
        if (jobRepository.claimManual(jobId, now) == 0) {
            throw new ConflictException("This job already has an active execution");
        }
        ScheduledJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job " + jobId + " was not found"));
        return executionRepository.save(new JobExecution(job, TriggerType.MANUAL)).getId();
    }

    @Transactional
    public boolean reserveRetry(long executionId) {
        int claimed = executionRepository.claimRetry(executionId, Instant.now());
        return claimed == 1;
    }

}
