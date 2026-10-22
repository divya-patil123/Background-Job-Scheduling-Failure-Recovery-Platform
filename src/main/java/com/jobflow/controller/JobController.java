package com.jobflow.controller;

import com.jobflow.dto.EnabledRequest;
import com.jobflow.dto.ExecutionResponse;
import com.jobflow.dto.JobResponse;
import com.jobflow.dto.JobUpsertRequest;
import com.jobflow.service.ExecutionDispatchService;
import com.jobflow.service.JobService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;
    private final ExecutionDispatchService dispatchService;

    public JobController(JobService jobService, ExecutionDispatchService dispatchService) {
        this.jobService = jobService;
        this.dispatchService = dispatchService;
    }

    @PostMapping
    public ResponseEntity<JobResponse> create(@Valid @RequestBody JobUpsertRequest request) {
        JobResponse response = jobService.create(request);
        return ResponseEntity.created(URI.create("/api/jobs/" + response.id())).body(response);
    }

    @GetMapping
    public List<JobResponse> findAll() {
        return jobService.findAll();
    }

    @GetMapping("/{id}")
    public JobResponse findById(@PathVariable long id) {
        return jobService.findById(id);
    }

    @PutMapping("/{id}")
    public JobResponse update(@PathVariable long id, @Valid @RequestBody JobUpsertRequest request) {
        return jobService.update(id, request);
    }

    @PatchMapping("/{id}/enabled")
    public JobResponse setEnabled(@PathVariable long id, @Valid @RequestBody EnabledRequest request) {
        return jobService.setEnabled(id, request.enabled());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        jobService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/trigger")
    public ResponseEntity<ExecutionResponse> trigger(@PathVariable long id) {
        return ResponseEntity.accepted().body(dispatchService.triggerManually(id));
    }
}
