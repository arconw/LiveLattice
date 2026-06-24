package io.livelattice.backgroundjobs.dto;

import java.util.List;

public class DeadLetterListResponse {

    private List<DeadLetterResponse> content;
    private long total;

    public DeadLetterListResponse(List<DeadLetterResponse> content, long total) {
        this.content = content;
        this.total = total;
    }

    public List<DeadLetterResponse> getContent() {
        return content;
    }

    public void setContent(List<DeadLetterResponse> content) {
        this.content = content;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
