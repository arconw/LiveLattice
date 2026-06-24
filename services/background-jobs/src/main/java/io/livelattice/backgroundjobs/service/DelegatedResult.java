package io.livelattice.backgroundjobs.service;

import io.livelattice.backgroundjobs.model.JobResult;
import java.util.UUID;

public class DelegatedResult extends JobResult {

    private final UUID downstreamJobId;

    public DelegatedResult(UUID downstreamJobId) {
        this.downstreamJobId = downstreamJobId;
        getData().put("delegated", true);
        getData().put("downstreamJobId", downstreamJobId.toString());
    }

    public UUID getDownstreamJobId() {
        return downstreamJobId;
    }
}
