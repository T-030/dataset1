package unaldi.creditcardservice.utils.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import unaldi.creditcardservice.utils.client.dto.InvoiceResponse;
import unaldi.creditcardservice.utils.client.dto.RestResponse;

import java.util.List;

/**
 * Copyright (c) 2024
 * All rights reserved.
 *
 * @author Emre Ünaldı
 */
@FeignClient(name = "invoice-service", url = "http://${INVOICE_SERVICE_HOST:localhost}:8085")
public interface InvoiceServiceClient {
    @GetMapping("/api/v1/invoices")
    ResponseEntity<RestResponse<List<InvoiceResponse>>> findAll();
}
