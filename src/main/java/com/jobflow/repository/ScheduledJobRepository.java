package com.jobflow.repository;

import com.jobflow.entity.ScheduledJob;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, Long> {

    List<ScheduledJob> findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
            Instant dueAt, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ScheduledJob j
            set j.executionLocked = true, j.lockAcquiredAt = :now, j.updatedAt = :now,
                j.nextRunAt = :nextRunAt, j.version = j.version + 1
            where j.id = :id and j.enabled = true and j.executionLocked = false
              and j.nextRunAt <= :now
            """)
    int claimScheduled(@Param("id") long id, @Param("now") Instant now,
                       @Param("nextRunAt") Instant nextRunAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ScheduledJob j
            set j.executionLocked = true, j.lockAcquiredAt = :now, j.updatedAt = :now,
                j.version = j.version + 1
            where j.id = :id and j.executionLocked = false
            """)
    int claimManual(@Param("id") long id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ScheduledJob j
            set j.executionLocked = false, j.lockAcquiredAt = null, j.updatedAt = :now,
                j.version = j.version + 1
            where j.id = :id
            """)
    int release(@Param("id") long id, @Param("now") Instant now);

}
