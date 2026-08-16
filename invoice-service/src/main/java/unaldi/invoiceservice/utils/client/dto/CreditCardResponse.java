package unaldi.invoiceservice.utils.client.dto;

import lombok.Builder;

import java.time.LocalDate;

/**
 * Copyright (c) 2024
 * All rights reserved.
 *
 * @author Emre Ünaldı
 */
@Builder
public record CreditCardResponse(
        Long id,
        String cardNumber,
        Long userId,
        LocalDate expirationDate,
        String cvv,
        Double creditLimit,
        Double debtAmount,
        Long bankId
) {
}
