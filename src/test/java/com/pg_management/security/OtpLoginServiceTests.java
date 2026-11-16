package com.pg_management.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class OtpLoginServiceTests {

	private final OwnerAccountRepository ownerAccountRepository = org.mockito.Mockito.mock(OwnerAccountRepository.class);

	@Test
	void otpRequestForAnUnregisteredNumberIsIndistinguishableFromAValidRequest() {
		when(ownerAccountRepository.findByMobileNumberAndApprovedTrueAndRemovedFalse("+919876543210"))
				.thenReturn(Optional.empty());
		OtpLoginService service = new OtpLoginService(
				ownerAccountRepository, "AC123", "test-token", "+14155552671");

		service.requestOtp("9876543210");

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.requestOtp("9876543210"));
		assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
		verify(ownerAccountRepository).findByMobileNumberAndApprovedTrueAndRemovedFalse("+919876543210");
	}

	@Test
	void otpRequestFailsWhenTwilioIsNotConfigured() {
		OtpLoginService service = new OtpLoginService(ownerAccountRepository, "", "", "");

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.requestOtp("9876543210"));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
		verifyNoInteractions(ownerAccountRepository);
	}

	@Test
	void verificationRejectsCodesWithoutAnActiveChallenge() {
		OtpLoginService service = new OtpLoginService(
				ownerAccountRepository, "AC123", "test-token", "+14155552671");

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.verifyOtp("9876543210", "123456"));

		assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
		verifyNoInteractions(ownerAccountRepository);
	}
}
