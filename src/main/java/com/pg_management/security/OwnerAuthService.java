package com.pg_management.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class OwnerAuthService {

	private final OwnerAccountRepository ownerAccountRepository;
	private final PasswordEncoder passwordEncoder;
	private final String setupToken;
	private final String registrationToken;
	private final JdbcTemplate jdbcTemplate;

	public OwnerAuthService(
			OwnerAccountRepository ownerAccountRepository,
			PasswordEncoder passwordEncoder,
			JdbcTemplate jdbcTemplate,
			@Value("${app.owner.setup-token:}") String setupToken,
			@Value("${app.owner.registration-token:}") String registrationToken) {
		this.ownerAccountRepository = ownerAccountRepository;
		this.passwordEncoder = passwordEncoder;
		this.jdbcTemplate = jdbcTemplate;
		this.setupToken = setupToken;
		this.registrationToken = registrationToken;
	}

	@Transactional(readOnly = true)
	public boolean setupRequired() {
		return ownerAccountRepository.count() == 0;
	}

	public void setupOwner(String providedSetupToken, String username, String password) {
		boolean firstOwner = ownerAccountRepository.count() == 0;
		checkToken(firstOwner ? setupToken : registrationToken, providedSetupToken,
				firstOwner ? "Owner setup is not configured" : "Owner registration is not configured");
		if (ownerAccountRepository.findByUsername(username).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already registered");
		}
		OwnerAccount owner = ownerAccountRepository.saveAndFlush(new OwnerAccount(
				username,
				passwordEncoder.encode(password)));
		jdbcTemplate.update("UPDATE residents SET owner_id = ? WHERE owner_id IS NULL", owner.getId());
	}

	public void resetPassword(String providedRecoveryToken, String username, String newPassword) {
		checkToken(setupToken, providedRecoveryToken, "Owner password recovery is not configured");
		OwnerAccount owner = ownerAccountRepository.findByUsername(username)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner account not found"));
		owner.changePassword(passwordEncoder.encode(newPassword));
		ownerAccountRepository.save(owner);
	}

	public void changePassword(String username, String currentPassword, String newPassword) {
		OwnerAccount owner = ownerAccountRepository.findByUsername(username)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Owner account not found"));
		if (!passwordEncoder.matches(currentPassword, owner.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
		}
		owner.changePassword(passwordEncoder.encode(newPassword));
		ownerAccountRepository.save(owner);
	}

	private void checkToken(String expectedToken, String providedToken, String missingTokenMessage) {
		if (expectedToken.isBlank()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, missingTokenMessage);
		}
		if (providedToken == null || !MessageDigest.isEqual(
				expectedToken.getBytes(StandardCharsets.UTF_8),
				providedToken.getBytes(StandardCharsets.UTF_8))) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token is invalid");
		}
	}
}
