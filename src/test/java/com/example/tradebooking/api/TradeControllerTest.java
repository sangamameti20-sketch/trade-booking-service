package com.example.tradebooking.api;

import com.example.tradebooking.api.dto.BookTradeRequest;
import com.example.tradebooking.api.dto.TradeResponse;
import com.example.tradebooking.service.TradeBookingService;
import com.example.tradebooking.service.TradeNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TradeController.class)
class TradeControllerTest {

    private static final UUID TRADE_ID = UUID.fromString("019a3d5e-7c1b-7d2e-9f3a-1b2c3d4e5f60");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TradeBookingService service;

    private static String body(String quantity, String rate) {
        return """
                {"borrower":"Goldman Sachs","lender":"BlackRock",
                 "security":"AAPL","quantity":%s,"rate":%s,"term":"OPEN","tradeDate":"2026-11-01"}
                """.formatted(quantity, rate);
    }

    @Test
    void booksValidTradeAndReturnsGeneratedId() throws Exception {
        when(service.book(any(BookTradeRequest.class))).thenReturn(new TradeResponse(
                TRADE_ID, "Goldman Sachs", "BlackRock", "AAPL", 10_000, new BigDecimal("0.25"),
                "OPEN", LocalDate.parse("2026-11-01"), "BOOKED", Instant.now()));

        mvc.perform(post("/api/v1/trades").contentType(MediaType.APPLICATION_JSON).content(body("10000", "0.25")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/trades/" + TRADE_ID))
                .andExpect(jsonPath("$.tradeId").value(TRADE_ID.toString()))
                .andExpect(jsonPath("$.status").value("BOOKED"));
    }

    @Test
    void rejectsClientSuppliedTradeId() throws Exception {
        String withId = body("10000", "0.25").replace("{", "{\"tradeId\":\"SL-1\",");
        mvc.perform(post("/api/v1/trades").contentType(MediaType.APPLICATION_JSON).content(withId))
                .andExpect(status().isBadRequest());
        verify(service, never()).book(any());
    }

    @Test
    void rejectsNonPositiveQuantityAndRate() throws Exception {
        mvc.perform(post("/api/v1/trades").contentType(MediaType.APPLICATION_JSON).content(body("0", "-0.5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations", hasSize(2)))
                .andExpect(jsonPath("$.violations[0].field").value("quantity"))
                .andExpect(jsonPath("$.violations[0].message").value("must be greater than 0"))
                .andExpect(jsonPath("$.violations[1].field").value("rate"))
                .andExpect(jsonPath("$.violations[1].message").value("must be greater than 0"));

        verify(service, never()).book(any());
    }

    @Test
    void rejectsZeroRate() throws Exception {
        mvc.perform(post("/api/v1/trades").contentType(MediaType.APPLICATION_JSON).content(body("100", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("rate"));
    }

    @Test
    void rejectsMissingFields() throws Exception {
        mvc.perform(post("/api/v1/trades").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations", hasSize(7)));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/v1/trades").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
    }

    @Test
    void returnsBadRequestForNonUuidTradeId() throws Exception {
        mvc.perform(get("/api/v1/trades/SL-1001")).andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForUnknownTrade() throws Exception {
        when(service.get(TRADE_ID)).thenThrow(new TradeNotFoundException(TRADE_ID));
        mvc.perform(get("/api/v1/trades/" + TRADE_ID)).andExpect(status().isNotFound());
    }
}
