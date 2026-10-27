package com.pg_management.resident.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.pg_management.resident.RentPayment;
import com.pg_management.resident.RentPaymentMethod;

public record RentPaymentResponse(
		int year,
		int month,
		boolean paid,
		BigDecimal amount,
		LocalDate paidDate,
		RentPaymentMethod method,
		boolean wasAlreadyPaid) {

	public static RentPaymentResponse from(RentPayment payment) {
		return new RentPaymentResponse(
				payment.getPeriodYear(),
				payment.getPeriodMonth(),
				payment.isPaid(),
				payment.getAmount(),
				payment.getPaidDate(),
				payment.getPaymentMethod(),
				false);
	}

	public static RentPaymentResponse from(RentPayment payment, boolean wasAlreadyPaid) {
		return new RentPaymentResponse(
				payment.getPeriodYear(),
				payment.getPeriodMonth(),
				payment.isPaid(),
				payment.getAmount(),
				payment.getPaidDate(),
				payment.getPaymentMethod(),
				wasAlreadyPaid);
	}
}
