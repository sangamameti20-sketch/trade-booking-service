package com.example.tradebooking.service;

import com.example.tradebooking.api.dto.BookTradeRequest;
import com.example.tradebooking.api.dto.TradeResponse;
import com.example.tradebooking.config.IdGenerator;
import com.example.tradebooking.config.KafkaTopicConfig.TradeEventsTopic;
import com.example.tradebooking.domain.Trade;
import com.example.tradebooking.event.TradeBookedEvent;
import com.example.tradebooking.outbox.OutboxWriter;
import com.example.tradebooking.repository.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TradeBookingService {

    private static final Logger log = LoggerFactory.getLogger(TradeBookingService.class);

    private final TradeRepository repository;
    private final OutboxWriter outbox;
    private final IdGenerator ids;
    private final String topic;

    public TradeBookingService(TradeRepository repository, OutboxWriter outbox, IdGenerator ids,
                               TradeEventsTopic topic) {
        this.repository = repository;
        this.outbox = outbox;
        this.ids = ids;
        this.topic = topic.name();
    }

    /**
     * Persists the trade and its TradeBooked event atomically. Kafka is not touched here; the
     * outbox relay publishes the event, so bookings succeed and no event is lost even if Kafka is down.
     */
    @Transactional
    public TradeResponse book(BookTradeRequest request) {
        Trade saved = repository.save(TradeMapper.toEntity(ids.newId(), request));

        var event = outbox.append(topic, "Trade", saved.getTradeId().toString(), TradeBookedEvent.TYPE,
                eventId -> TradeMapper.toEvent(eventId, saved));

        log.atInfo()
                .addKeyValue("tradeId", saved.getTradeId())
                .addKeyValue("eventId", event.getId())
                .addKeyValue("security", saved.getSecurity())
                .addKeyValue("quantity", saved.getQuantity())
                .addKeyValue("rate", saved.getRate())
                .addKeyValue("term", saved.getTerm())
                .log("Trade booked");

        return TradeMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TradeResponse get(UUID tradeId) {
        return repository.findById(tradeId)
                .map(TradeMapper::toResponse)
                .orElseThrow(() -> new TradeNotFoundException(tradeId));
    }
}
