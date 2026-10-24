package com.pg_management.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
	void passwordResetOtpIsSentAndOnlyItsHashIsStored() {
		OwnerOtpService service = new OwnerOtpService(
				challengeRepository, ownerAccountRepository, passwordEncoder, whatsAppClient);
		when(ownerAccountRepository.findByUsernameAndWhatsappNumber("owner", "+919876543210"))
				.thenReturn(Optional.of(new OwnerAccount("owner", "hash", "Sample PG", "+919876543210")));
		when(challengeRepository.save(any(OwnerOtpChallenge.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		service.requestPasswordResetOtp("owner", "9876543210", "127.0.0.1");

		ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
		verify(whatsAppClient).sendOtp(org.mockito.ArgumentMatchers.eq("+919876543210"), code.capture());
		ArgumentCaptor<OwnerOtpChallenge> challenge = ArgumentCaptor.forClass(OwnerOtpChallenge.class);
		verify(challengeRepository).save(challenge.capture());
		assertTrue(code.getValue().matches("[0-9]{6}"));
		assertTrue(passwordEncoder.matches(code.getValue(), challenge.getValue().getCodeHash()));
		assertTrue(challenge.getValue().getExpiresAt().isAfter(Instant.now()));
		assertEquals(OwnerOtpPurpose.PASSWORD_RESET, challenge.getValue().getPurpose());
	}
}
