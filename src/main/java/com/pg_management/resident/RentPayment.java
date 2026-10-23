package com.pg_management.resident;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "rent_payments", uniqueConstraints = @UniqueConstraint(
		name = "uk_rent_payment_resident_period",
		columnNames = {"resident_id", "period_year", "period_month"}))
public class RentPayment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "resident_id", nullable = false)
	private Resident resident;

	@Column(name = "period_year", nullable = false)
	private int periodYear;

	@Column(name = "period_month", nullable = false)
	private int periodMonth;

	@Column(nullable = false)
	private boolean paid;

	@Column(precision = 10, scale = 2)
	private BigDecimal amount;

	@Column(name = "paid_date")
	private LocalDate paidDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_method", length = 20)
	private RentPaymentMethod paymentMethod;

	@Column(name = "reminder_sent_at")
	private Instant reminderSentAt;

	protected RentPayment() {
	}

	public RentPayment(Resident resident, int periodYear, int periodMonth) {
		this.resident = resident;
		this.periodYear = periodYear;
		this.periodMonth = periodMonth;
	}

	public int getPeriodYear() {
		return periodYear;
	}

	public int getPeriodMonth() {
		return periodMonth;
	}

	public boolean isPaid() {
		return paid;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public LocalDate getPaidDate() {
		return paidDate;
	}

	public RentPaymentMethod getPaymentMethod() {
		return paymentMethod;
	}

	public Instant getReminderSentAt() {
		return reminderSentAt;
	}

	public void markPaid(BigDecimal amount, LocalDate paidDate, RentPaymentMethod paymentMethod) {
		this.paid = true;
		this.amount = amount;
		this.paidDate = paidDate;
		this.paymentMethod = paymentMethod;
	}

	public void setReminderSentAt(Instant reminderSentAt) {
		this.reminderSentAt = reminderSentAt;
	}
}
