package com.java.hotelbooking.integration;

import com.java.hotelbooking.configuration.SimulationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Client of a (simulated) bank API that converts prices from UAH. Unknown currencies fail the future.
 */
@Component
@RequiredArgsConstructor
public class ExchangeRateClient {

    static final Map<String, BigDecimal> RATES_FROM_UAH = Map.of(
            "UAH", BigDecimal.ONE,
            "USD", new BigDecimal("0.0241"),
            "EUR", new BigDecimal("0.0222"),
            "PLN", new BigDecimal("0.0962")
    );

    private final SlowCallSimulator slowCall;
    private final SimulationProperties properties;

    @Async
    public CompletableFuture<BigDecimal> convertFromUah(BigDecimal amount, String currency) {
        slowCall.simulate("exchange-rate", properties.exchangeRateDelay());
        BigDecimal rate = RATES_FROM_UAH.get(currency.toUpperCase(Locale.ROOT));
        if (rate == null) {
            throw new ExchangeRateUnavailableException("No exchange rate from UAH to " + currency);
        }
        return CompletableFuture.completedFuture(amount.multiply(rate).setScale(2, RoundingMode.HALF_UP));
    }
}
