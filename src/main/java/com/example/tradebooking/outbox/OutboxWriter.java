package com.example.tradebooking.outbox;

import com.example.tradebooking.config.IdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Function;

/** Records an event in the outbox as part of the caller's transaction. */
@Component
public class OutboxWriter {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final IdGenerator ids;

    public OutboxWriter(OutboxRepository repository, ObjectMapper objectMapper, IdGenerator ids) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.ids = ids;
    }

    /**
     * @param eventFactory builds the payload given the outbox event id, so the id stored in the
     *                     row and the eventId inside the message are the same value.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> OutboxEvent append(String topic, String aggregateType, String aggregateId, String eventType,
                                  Function<UUID, T> eventFactory) {
        UUID eventId = ids.newId();
        String payload;
        try {
            payload = objectMapper.writeValueAsString(eventFactory.apply(eventId));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + eventType + " event", e);
        }
        return repository.save(new OutboxEvent(eventId, aggregateType, aggregateId, eventType, topic, payload));
    }
}
