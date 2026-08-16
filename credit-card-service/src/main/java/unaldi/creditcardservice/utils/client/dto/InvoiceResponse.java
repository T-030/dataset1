package unaldi.creditcardservice.utils.client.dto;

import lombok.Builder;
import unaldi.creditcardservice.utils.client.enums.PaymentStatus;

import java.time.LocalDate;

/**
 * Copyright (c) 2024
 * All rights reserved.
 *
 * @author Emre Ünaldı
 */
@Builder
public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        Long userId,
        Double amount,
        LocalDate invoiceDate,
        LocalDate dueDate,
        PaymentStatus paymentStatus
) {
}
