package com.jobflow.controller;

import com.jobflow.dto.ExecutionDetailResponse;
import com.jobflow.dto.ExecutionResponse;
import com.jobflow.enums.ExecutionStatus;
import com.jobflow.service.ExecutionQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class ExecutionController {

    private final ExecutionQueryService queryService;

    public ExecutionController(ExecutionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/jobs/{jobId}/executions")
    public Page<ExecutionResponse> findForJob(
            @PathVariable long jobId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queryService.findForJob(jobId, pageable(page, size));
    }

    @GetMapping("/executions")
    public Page<ExecutionResponse> findAll(
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) ExecutionStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queryService.findAll(jobId, status, pageable(page, size));
    }

    @GetMapping("/executions/{id}")
    public ExecutionDetailResponse findById(@PathVariable long id) {
        return queryService.findDetail(id);
    }

    private Pageable pageable(int page, int size) {
        Sort stableNewestFirst = Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequest.of(page, size, stableNewestFirst);
    }
}
