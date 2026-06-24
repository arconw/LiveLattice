package io.livelattice.backgroundjobs.dto;

import java.util.List;

public class JobListResponse {

    private List<JobResponse> content;
    private int page;
    private int size;
    private long total;

    public JobListResponse(List<JobResponse> content, int page, int size, long total) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.total = total;
    }

    public List<JobResponse> getContent() {
        return content;
    }

    public void setContent(List<JobResponse> content) {
        this.content = content;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
