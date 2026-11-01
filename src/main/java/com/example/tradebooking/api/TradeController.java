package com.example.tradebooking.api;

import com.example.tradebooking.api.dto.BookTradeRequest;
import com.example.tradebooking.api.dto.TradeResponse;
import com.example.tradebooking.service.TradeBookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trades")
public class TradeController {

    private final TradeBookingService service;

    public TradeController(TradeBookingService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TradeResponse> book(@Valid @RequestBody BookTradeRequest request) {
        TradeResponse booked = service.book(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{tradeId}").buildAndExpand(booked.tradeId()).toUri();
        return ResponseEntity.created(location).body(booked);
    }

    @GetMapping("/{tradeId}")
    public TradeResponse get(@PathVariable UUID tradeId) {
        return service.get(tradeId);
    }
}
