package com.pg_management.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.pg_management.whatsapp.WhatsAppCloudApiClient;

@ExtendWith(MockitoExtension.class)
class OwnerOtpServiceTests {

	@Mock
	private OwnerOtpChallengeRepository challengeRepository;
	@Mock
	private OwnerAccountRepository ownerAccountRepository;
	@Mock
	private WhatsAppCloudApiClient whatsAppClient;

	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

	@Test
	void registrationOtpIsSentAndOnlyItsHashIsStored() {
		OwnerOtpService service = new OwnerOtpService(
				challengeRepository, ownerAccountRepository, passwordEncoder, whatsAppClient, "+919876543210");
		when(challengeRepository.countByWhatsappNumberAndPurposeAndCreatedAtAfter(
				any(), any(), any())).thenReturn(0L);
		when(challengeRepository.countByRequesterKeyAndCreatedAtAfter(any(), any())).thenReturn(0L);
		when(challengeRepository.save(any(OwnerOtpChallenge.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		service.requestRegistrationOtp("127.0.0.1");

		ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
		verify(whatsAppClient).sendOtp(org.mockito.ArgumentMatchers.eq("+919876543210"), code.capture());
		ArgumentCaptor<OwnerOtpChallenge> challenge = ArgumentCaptor.forClass(OwnerOtpChallenge.class);
		verify(challengeRepository).save(challenge.capture());
		assertTrue(code.getValue().matches("[0-9]{6}"));
		assertTrue(passwordEncoder.matches(code.getValue(), challenge.getValue().getCodeHash()));
		assertTrue(challenge.getValue().getExpiresAt().isAfter(Instant.now()));
	}

	@Test
	void registrationUsesAdminOtpAndAddsIndiaCountryCodeToOwnerNumber() {
		OwnerOtpService service = new OwnerOtpService(
				challengeRepository, ownerAccountRepository, passwordEncoder, whatsAppClient, "+919999999999");
		OwnerOtpChallenge challenge = new OwnerOtpChallenge(
				"+919999999999", OwnerOtpPurpose.REGISTRATION, passwordEncoder.encode("123456"),
				"requester", Instant.now(), Instant.now().plus(Duration.ofMinutes(5)));
		when(ownerAccountRepository.findByUsername("new-owner")).thenReturn(Optional.empty());
		when(ownerAccountRepository.findByWhatsappNumber("+919876543210")).thenReturn(Optional.empty());
		when(challengeRepository.findFirstByWhatsappNumberAndPurposeOrderByCreatedAtDesc(
				"+919999999999", OwnerOtpPurpose.REGISTRATION)).thenReturn(Optional.of(challenge));
		when(ownerAccountRepository.saveAndFlush(any(OwnerAccount.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		OwnerAccount owner = service.registerOwner(
				"new-owner", "a-long-test-password", "Sample PG", "9876543210", "123456");

		assertEquals("+919876543210", owner.getWhatsappNumber());
		verify(challengeRepository).delete(challenge);
	}
}
