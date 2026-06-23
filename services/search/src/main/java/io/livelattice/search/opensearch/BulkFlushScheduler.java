package io.livelattice.search.opensearch;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BulkFlushScheduler {

    private final IndexEventProcessor processor;

    public BulkFlushScheduler(IndexEventProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${livelattice.search.bulk-flush-interval:PT5S}")
    public void scheduledFlush() {
        processor.flush();
    }
}
