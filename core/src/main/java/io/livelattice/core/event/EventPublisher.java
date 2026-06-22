package io.livelattice.core.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {

    private final ApplicationEventPublisher publisher;

    public EventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(CanvasCreated event) {
        publisher.publishEvent(event);
    }

    public void publish(CanvasUpdated event) {
        publisher.publishEvent(event);
    }

    public void publish(CanvasDeleted event) {
        publisher.publishEvent(event);
    }

    public void publish(CommentAdded event) {
        publisher.publishEvent(event);
    }
}
