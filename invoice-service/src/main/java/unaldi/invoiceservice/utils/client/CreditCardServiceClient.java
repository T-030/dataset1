package unaldi.invoiceservice.utils.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import unaldi.invoiceservice.utils.client.dto.CreditCardResponse;
import unaldi.invoiceservice.utils.client.dto.RestResponse;
import unaldi.invoiceservice.utils.client.dto.UserResponse;
import unaldi.invoiceservice.utils.client.request.CreditCardUpdateRequest;

import java.util.List;

/**
 * Copyright (c) 2024
 * All rights reserved.
 *
 * @author Emre Ünaldı
 */
@FeignClient(name = "credit-card-service", url = "http://${CREDIT_CARD_SERVICE_HOST:localhost}:8083")
public interface CreditCardServiceClient {
    @GetMapping("/api/v1/creditCards/users/{userId}")
    ResponseEntity<RestResponse<UserResponse>> findCreditCardUserByUserId(@PathVariable Long userId);

    @GetMapping("/api/v1/creditCards")
    ResponseEntity<RestResponse<List<CreditCardResponse>>> findAll();

    @PutMapping("/api/v1/creditCards")
    ResponseEntity<RestResponse<CreditCardResponse>> update(@RequestBody CreditCardUpdateRequest creditCardUpdateRequest);
}
