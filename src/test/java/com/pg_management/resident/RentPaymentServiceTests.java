package com.pg_management.resident;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pg_management.resident.dto.RentPaymentRequest;
import com.pg_management.security.OwnerAccount;
import com.pg_management.whatsapp.WhatsAppCloudApiClient;

@ExtendWith(MockitoExtension.class)
class RentPaymentServiceTests {

	@Mock
	private ResidentRepository residentRepository;
	@Mock
	private RentPaymentRepository rentPaymentRepository;
	@Mock
	private WhatsAppCloudApiClient whatsAppClient;

	@Test
	void markingRentPaidStoresPaymentAndSendsConfirmation() {
		OwnerAccount owner = mock(OwnerAccount.class);
		when(owner.getId()).thenReturn(4L);
		when(owner.getPgName()).thenReturn("Green PG");
		Resident resident = new Resident(owner, "Resident One", "Parent", "+919876543210", "101",
				new BigDecimal("12000.00"), BigDecimal.ZERO, LocalDate.of(2026, 1, 1), "Address", "Office");
		when(residentRepository.findByIdAndOwner_Id(8L, 4L)).thenReturn(Optional.of(resident));
		when(rentPaymentRepository.findByResidentAndPeriodYearAndPeriodMonth(resident, 2026, 1))
				.thenReturn(Optional.empty());
		when(rentPaymentRepository.saveAndFlush(any(RentPayment.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		RentPaymentService service = new RentPaymentService(
				residentRepository, rentPaymentRepository, whatsAppClient);

		var result = service.recordPaid(owner, 8L, 2026, 1,
				new RentPaymentRequest(new BigDecimal("12000.00"), LocalDate.of(2026, 1, 5), RentPaymentMethod.UPI));

		assertTrue(result.paid());
		assertTrue(result.whatsappNotificationSent());
		verify(whatsAppClient).sendRentPaid(
				eq("+919876543210"), eq("Green PG"), eq("Resident One"), eq("January 2026"),
				eq("12000.00"), eq("UPI"));
	}
}
