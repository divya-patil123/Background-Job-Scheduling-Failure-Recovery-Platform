package com.jobflow.repository;

import com.jobflow.entity.JobExecution;
import com.jobflow.enums.ExecutionStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {

    Page<JobExecution> findByJob_Id(long jobId, Pageable pageable);

    @Query("""
            select e from JobExecution e
            where (:jobId is null or e.job.id = :jobId)
              and (:status is null or e.status = :status)
            """)
    Page<JobExecution> search(@Param("jobId") Long jobId,
                              @Param("status") ExecutionStatus status,
                              Pageable pageable);

    List<JobExecution> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            ExecutionStatus status, Instant now);

    Page<JobExecution> findByStatusOrderByCreatedAtAscIdAsc(ExecutionStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update JobExecution e
            set e.status = com.jobflow.enums.ExecutionStatus.QUEUED, e.nextRetryAt = null
            where e.id = :id and e.status = com.jobflow.enums.ExecutionStatus.RETRYING
              and e.nextRetryAt <= :now
            """)
    int claimRetry(@Param("id") long id, @Param("now") Instant now);

    boolean existsByJob_Id(long jobId);

}
