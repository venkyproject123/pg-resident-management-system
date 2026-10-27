package com.pg_management.resident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
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
}
