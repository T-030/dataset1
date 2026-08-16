package unaldi.creditcardservice.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Copyright (c) 2024
 * All rights reserved.
 *
 * @author Emre Ünaldı
 */
@Repository
public class OutstandingDebtRepository {
    private static final String SUM_UNPAID_BY_USER = """
            SELECT COALESCE(SUM(amount), 0)
            FROM invoices
            WHERE user_id = ?
              AND payment_status IN ('PENDING', 'OVERDUE', 'FAILED')
            """;

    private static final String SETTLE_UNPAID_BY_USER = """
            UPDATE invoices
            SET payment_status = 'PAID'
            WHERE user_id = ?
              AND payment_status IN ('PENDING', 'OVERDUE', 'FAILED')
            """;

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public OutstandingDebtRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Double sumUnpaidByUserId(Long userId) {
        Double total = this.jdbcTemplate.queryForObject(SUM_UNPAID_BY_USER, Double.class, userId);

        return total == null ? 0.0 : total;
    }

    public int settleUnpaidByUserId(Long userId) {
        return this.jdbcTemplate.update(SETTLE_UNPAID_BY_USER, userId);
    }
}
