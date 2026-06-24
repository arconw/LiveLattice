package io.livelattice.backgroundjobs.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class JobHandlerRegistry {

    private final Map<String, JobHandler> handlers;

    public JobHandlerRegistry(List<JobHandler> handlers) {
        this.handlers = handlers.stream().collect(Collectors.toMap(h -> h.type().toUpperCase(), h -> h));
    }

    public JobHandler get(String type) {
        return handlers.get(type.toUpperCase());
    }
}
