package com.java.hotelbooking.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Result of two independent slow calls combined: the rendered invoice and the price in another currency.
 * {@code convertedTotal} is null when the conversion failed or timed out; {@code note} then says why.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingInvoiceResponse(
        Long bookingId,
        String invoiceNumber,
        int invoiceSizeBytes,
        BigDecimal totalUah,
        String currency,
        BigDecimal convertedTotal,
        String note
) {
}
