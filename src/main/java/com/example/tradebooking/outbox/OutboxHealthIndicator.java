package com.example.tradebooking.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports the outbox backlog under /actuator/health. It never marks the service DOWN: bookings
 * keep working while Kafka is unavailable. A large backlog only adds a warning to the details.
 */
@Component("outbox")
public class OutboxHealthIndicator implements HealthIndicator {

    private final OutboxRepository repository;
    private final long warnThreshold;

    public OutboxHealthIndicator(OutboxRepository repository,
                                 @Value("${app.outbox.backlog-warn-threshold:1000}") long warnThreshold) {
        this.repository = repository;
        this.warnThreshold = warnThreshold;
    }

    @Override
    public Health health() {
        long pending = repository.countByPublishedAtIsNull();
        var builder = Health.up().withDetail("pendingEvents", pending);
        if (pending >= warnThreshold) {
            builder.withDetail("warning", "outbox backlog above " + warnThreshold + "; check Kafka connectivity");
        }
        return builder.build();
    }
}
