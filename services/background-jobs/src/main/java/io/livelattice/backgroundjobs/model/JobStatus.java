package io.livelattice.backgroundjobs.model;

public enum JobStatus {
    QUEUED,
    SCHEDULED,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
    RETRYING,
    DEAD
}
