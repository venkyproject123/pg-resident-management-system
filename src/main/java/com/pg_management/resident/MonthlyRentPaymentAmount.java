package com.pg_management.resident;

import java.math.BigDecimal;

public record MonthlyRentPaymentAmount(Long residentId, boolean paid, BigDecimal amount) {
}
