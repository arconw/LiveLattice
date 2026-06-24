package io.livelattice.backgroundjobs.controller;

import io.livelattice.backgroundjobs.dto.CreateJobRequest;
import io.livelattice.backgroundjobs.dto.DeadLetterListResponse;
import io.livelattice.backgroundjobs.dto.DeadLetterResponse;
import io.livelattice.backgroundjobs.dto.JobListResponse;
import io.livelattice.backgroundjobs.dto.JobResponse;
import io.livelattice.backgroundjobs.model.DeadLetter;
import io.livelattice.backgroundjobs.model.JobDefinition;
import io.livelattice.backgroundjobs.model.JobStatus;
import io.livelattice.backgroundjobs.service.DeadLetterManager;
import io.livelattice.backgroundjobs.service.JobService;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;
    private final DeadLetterManager deadLetterManager;

    public JobController(JobService jobService, DeadLetterManager deadLetterManager) {
        this.jobService = jobService;
        this.deadLetterManager = deadLetterManager;
    }

    @PostMapping("/export")
    public ResponseEntity<JobResponse> enqueueExport(@RequestBody CreateJobRequest request,
                                                     @RequestHeader("x-auth-subject") String ownerSubject,
                                                     @RequestHeader("x-auth-roles") String rolesHeader) {
        request.setType("EXPORT");
        request.setOwnerSubject(ownerSubject);
        JobDefinition job = jobService.createJob(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobService.getJob(job.getId(), ownerSubject, rolesHeader));
    }

    @PostMapping("/import")
    public ResponseEntity<JobResponse> enqueueImport(@RequestBody CreateJobRequest request,
                                                     @RequestHeader("x-auth-subject") String ownerSubject,
                                                     @RequestHeader("x-auth-roles") String rolesHeader) {
        request.setType("IMPORT");
        request.setOwnerSubject(ownerSubject);
        JobDefinition job = jobService.createJob(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobService.getJob(job.getId(), ownerSubject, rolesHeader));
    }

    @PostMapping
    public ResponseEntity<JobResponse> enqueueJob(@Valid @RequestBody CreateJobRequest request,
                                                  @RequestHeader("x-auth-subject") String ownerSubject,
                                                  @RequestHeader("x-auth-roles") String rolesHeader) {
        request.setOwnerSubject(ownerSubject);
        JobDefinition job = jobService.createJob(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobService.getJob(job.getId(), ownerSubject, rolesHeader));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getJob(@PathVariable UUID jobId,
                                              @RequestHeader("x-auth-subject") String requesterSubject,
                                              @RequestHeader("x-auth-roles") String rolesHeader) {
        return ResponseEntity.ok(jobService.getJobWithProgress(jobId, requesterSubject, rolesHeader));
    }

    @GetMapping
    public ResponseEntity<JobListResponse> listJobs(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "workspaceId", required = false) UUID workspaceId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestHeader("x-auth-subject") String requesterSubject,
            @RequestHeader("x-auth-roles") String rolesHeader) {
        JobStatus jobStatus = status != null ? JobStatus.valueOf(status.toUpperCase()) : null;
        return ResponseEntity.ok(jobService.listJobs(type, jobStatus, workspaceId, page, size, requesterSubject, rolesHeader));
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<JobResponse> cancelJob(@PathVariable UUID jobId,
                                                   @RequestHeader("x-auth-subject") String requesterSubject,
                                                   @RequestHeader("x-auth-roles") String rolesHeader) {
        return ResponseEntity.ok(jobService.cancelJob(jobId, requesterSubject, rolesHeader));
    }

    @GetMapping("/dead-letters")
    public ResponseEntity<DeadLetterListResponse> listDeadLetters(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestHeader("x-auth-subject") String requesterSubject,
            @RequestHeader("x-auth-roles") String rolesHeader) {
        Pageable pageable = PageRequest.of(page, size);
        Page<DeadLetter> result = deadLetterManager.listDeadLetters(requesterSubject, rolesHeader, pageable);
        return ResponseEntity.ok(new DeadLetterListResponse(
            result.getContent().stream().map(DeadLetterResponse::new).collect(Collectors.toList()),
            result.getTotalElements()
        ));
    }

    @PostMapping("/dead-letters/{id}/retry")
    public ResponseEntity<JobResponse> retryDeadLetter(@PathVariable UUID id,
                                                       @RequestHeader("x-auth-subject") String requesterSubject,
                                                       @RequestHeader("x-auth-roles") String rolesHeader) {
        JobDefinition job = deadLetterManager.retryDeadLetter(id, requesterSubject, rolesHeader);
        return ResponseEntity.ok(jobService.getJob(job.getId(), requesterSubject, rolesHeader));
    }
}
