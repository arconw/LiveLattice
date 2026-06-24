package io.livelattice.auditlog.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StorageReadyListener {

    private final StorageConfig.MinioBucketInitializer bucketInitializer;

    public StorageReadyListener(StorageConfig.MinioBucketInitializer bucketInitializer) {
        this.bucketInitializer = bucketInitializer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        bucketInitializer.initialize();
    }
}
