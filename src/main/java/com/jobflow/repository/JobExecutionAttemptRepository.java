package com.jobflow.repository;

import com.jobflow.entity.JobExecutionAttempt;
import com.jobflow.enums.AttemptStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobExecutionAttemptRepository extends JpaRepository<JobExecutionAttempt, Long> {

    List<JobExecutionAttempt> findByExecution_IdOrderByAttemptNumberAsc(long executionId);

    Page<JobExecutionAttempt> findByStatusOrderByStartedAtAscIdAsc(AttemptStatus status, Pageable pageable);
}
