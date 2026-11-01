package com.example.tradebooking.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Inbound request to book a securities lending trade. The tradeId is assigned by the service.
 *
 * @param term  "OPEN" for an open (callable) loan, or a fixed tenor such as "30D", "3M", "1Y".
 * @param rate  lending fee / rebate rate in percent, e.g. 0.25 for 25bps.
 */
public record BookTradeRequest(
        @NotBlank @Size(max = 128)
        String borrower,

        @NotBlank @Size(max = 128)
        String lender,

        @NotBlank @Size(max = 16)
        @Pattern(regexp = "^[A-Z0-9.]{1,16}$", message = "must be an upper-case ticker, e.g. AAPL or BRK.B")
        String security,

        @NotNull @Positive(message = "must be greater than 0")
        Long quantity,

        @NotNull
        @DecimalMin(value = "0", inclusive = false, message = "must be greater than 0")
        @Digits(integer = 6, fraction = 6)
        BigDecimal rate,

        @NotBlank
        @Pattern(regexp = "^(OPEN|[1-9][0-9]{0,3}[DWMY])$", message = "must be OPEN or a tenor like 30D, 3M, 1Y")
        String term,

        @NotNull
        LocalDate tradeDate
) {
}
