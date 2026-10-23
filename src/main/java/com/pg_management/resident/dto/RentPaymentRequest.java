package com.pg_management.resident.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.pg_management.resident.RentPaymentMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record RentPaymentRequest(
		@NotNull @DecimalMin(value = "0.01") BigDecimal amount,
		@NotNull LocalDate paidDate,
		@NotNull RentPaymentMethod method) {
}
