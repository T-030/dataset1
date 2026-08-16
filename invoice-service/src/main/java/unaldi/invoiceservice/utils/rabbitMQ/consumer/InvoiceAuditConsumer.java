package unaldi.invoiceservice.utils.rabbitMQ.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import unaldi.invoiceservice.utils.rabbitMQ.request.LogRequest;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Copyright (c) 2024
 * All rights reserved.
 *
 * @author Emre Ünaldı
 */
@Component
@Slf4j
public class InvoiceAuditConsumer {
    private static final String INVOICE_SERVICE_NAME = "invoice-service";

    private final AtomicLong invoiceOperationCount = new AtomicLong();

    @RabbitListener(queues = "${rabbitmq.logs.queue}")
    public void trackInvoiceOperations(LogRequest logRequest) {
        if (logRequest == null || !INVOICE_SERVICE_NAME.equals(logRequest.getServiceName())) {
            return;
        }

        long total = this.invoiceOperationCount.incrementAndGet();

        log.debug("Invoice operation counted : {} - {}", logRequest.getOperationType(), total);
    }

    public long getInvoiceOperationCount() {
        return this.invoiceOperationCount.get();
    }
}
