package com.pg_management.resident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pg_management.resident.dto.RentPaymentRequest;
import com.pg_management.security.OwnerAccount;

@ExtendWith(MockitoExtension.class)
class RentPaymentServiceTests {

	@Mock
	private ResidentRepository residentRepository;
	@Mock
	private RentPaymentRepository rentPaymentRepository;
	@Test
	void markingRentPaidStoresPayment() {
		OwnerAccount owner = mock(OwnerAccount.class);
		when(owner.getId()).thenReturn(4L);
		Resident resident = new Resident(owner, "Resident One", "Parent", "+919876543210", "101",
				new BigDecimal("12000.00"), BigDecimal.ZERO, LocalDate.of(2026, 1, 1), "Address", "Office");
		when(residentRepository.findByIdAndOwner_Id(8L, 4L)).thenReturn(Optional.of(resident));
		when(rentPaymentRepository.findByResidentAndPeriodYearAndPeriodMonth(resident, 2026, 1))
				.thenReturn(Optional.empty());
		when(rentPaymentRepository.saveAndFlush(any(RentPayment.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		RentPaymentService service = new RentPaymentService(residentRepository, rentPaymentRepository);

		var result = service.recordPaid(owner, 8L, 2026, 1,
				new RentPaymentRequest(new BigDecimal("12000.00"), LocalDate.of(2026, 1, 5), RentPaymentMethod.UPI));

		org.junit.jupiter.api.Assertions.assertTrue(result.paid());
		org.junit.jupiter.api.Assertions.assertFalse(result.wasAlreadyPaid());
	}

	@Test
	void rentHistoryExcludesMonthsBeforeJoiningMonth() {
		OwnerAccount owner = mock(OwnerAccount.class);
		when(owner.getId()).thenReturn(4L);
		Resident resident = new Resident(owner, "Resident One", "Parent", "+919876543210", "101",
				new BigDecimal("12000.00"), BigDecimal.ZERO, LocalDate.of(2026, 3, 20), "Address", "Office");
		when(residentRepository.findByIdAndOwner_Id(8L, 4L)).thenReturn(Optional.of(resident));
		when(rentPaymentRepository.findAllByResidentAndPeriodYearOrderByPeriodMonthAsc(resident, 2026))
				.thenReturn(List.of(
						new RentPayment(resident, 2026, 1),
						new RentPayment(resident, 2026, 3),
						new RentPayment(resident, 2026, 4)));
		RentPaymentService service = new RentPaymentService(residentRepository, rentPaymentRepository);

		var payments = service.findYear(owner, 8L, 2026);

		assertEquals(List.of(3, 4), payments.stream().map(payment -> payment.month()).toList());
	}

	@Test
	void rentCannotBeRecordedBeforeJoiningMonth() {
		OwnerAccount owner = mock(OwnerAccount.class);
		when(owner.getId()).thenReturn(4L);
		Resident resident = new Resident(owner, "Resident One", "Parent", "+919876543210", "101",
				new BigDecimal("12000.00"), BigDecimal.ZERO, LocalDate.of(2026, 3, 20), "Address", "Office");
		when(residentRepository.findByIdAndOwner_Id(8L, 4L)).thenReturn(Optional.of(resident));
		RentPaymentService service = new RentPaymentService(residentRepository, rentPaymentRepository);

		var exception = org.junit.jupiter.api.Assertions.assertThrows(
				org.springframework.web.server.ResponseStatusException.class,
				() -> service.recordPaid(owner, 8L, 2026, 2,
						new RentPaymentRequest(new BigDecimal("12000.00"), LocalDate.of(2026, 2, 5), RentPaymentMethod.UPI)));

		assertEquals(400, exception.getStatusCode().value());
		verify(rentPaymentRepository, never()).saveAndFlush(any());
	}

	@Test
	void monthlyReportSumsRentCollectedAndRemainingWithoutCheckingDueDates() {
		OwnerAccount owner = mock(OwnerAccount.class);
		when(owner.getId()).thenReturn(4L);
		LocalDate today = LocalDate.now();
		YearMonth month = YearMonth.from(today);
		Resident residentOne = new Resident(owner, "Resident One", "Parent", "+919876543210", "101", 2,
				new BigDecimal("12000.00"), BigDecimal.ZERO, month.minusMonths(2).atDay(25), "Address", "Office");
		Resident residentTwo = new Resident(owner, "Resident Two", "Parent", "+919876543211", "102", 3,
				new BigDecimal("5000.00"), BigDecimal.ZERO, month.atDay(1), "Address", "Office");
		Resident futureResident = new Resident(owner, "Future Resident", "Parent", "+919876543212", "103", 2,
				new BigDecimal("9000.00"), BigDecimal.ZERO, today.plusDays(1), "Address", "Office");
		Resident pastResident = new Resident(owner, "Past Resident", "Parent", "+919876543213", "104", 1,
				new BigDecimal("7000.00"), BigDecimal.ZERO, month.minusMonths(2).atDay(1), "Address", "Office");
		pastResident.markVacated();
		org.springframework.test.util.ReflectionTestUtils.setField(residentOne, "id", 8L);
		org.springframework.test.util.ReflectionTestUtils.setField(residentTwo, "id", 9L);
		org.springframework.test.util.ReflectionTestUtils.setField(futureResident, "id", 10L);
		org.springframework.test.util.ReflectionTestUtils.setField(pastResident, "id", 11L);
		org.springframework.test.util.ReflectionTestUtils.setField(
				pastResident, "vacatedDate", month.minusMonths(1).atEndOfMonth());
		RentPayment paidPayment = new RentPayment(residentOne, month.getYear(), month.getMonthValue());
		paidPayment.markPaid(new BigDecimal("8000.00"), today, RentPaymentMethod.UPI);
		when(residentRepository.findAllByOwner_IdOrderByRoomNumberAscNameAsc(4L))
				.thenReturn(List.of(residentOne, residentTwo, futureResident, pastResident));
		when(rentPaymentRepository.findAllByResident_Owner_IdAndPeriodYearAndPeriodMonth(
				4L, month.getYear(), month.getMonthValue())).thenReturn(List.of(paidPayment));
		RentPaymentService service = new RentPaymentService(residentRepository, rentPaymentRepository);

		var report = service.findMonthlyPayments(owner);

		assertEquals(month.getYear(), report.year());
		assertEquals(month.getMonthValue(), report.month());
		assertEquals(new BigDecimal("17000.00"), report.totalRent());
		assertEquals(new BigDecimal("8000.00"), report.totalCollected());
		assertEquals(new BigDecimal("9000.00"), report.totalRemaining());
		assertEquals(List.of(8L, 9L), report.residents().stream()
				.map(MonthlyRentPaymentResponse.ResidentPayment::residentId).toList());
		assertEquals(new BigDecimal("4000.00"), report.residents().get(0).remaining());
	}
}
