package com.pg_management.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

public class TimedPasswordEncoder implements PasswordEncoder {

	private static final Logger LOGGER = LoggerFactory.getLogger(TimedPasswordEncoder.class);

	private final PasswordEncoder delegate;
	private final long slowOperationThresholdMs;

	public TimedPasswordEncoder(PasswordEncoder delegate, long slowOperationThresholdMs) {
		this.delegate = delegate;
		this.slowOperationThresholdMs = slowOperationThresholdMs;
	}

	@Override
	public String encode(CharSequence rawPassword) {
		return delegate.encode(rawPassword);
	}

	@Override
	public boolean matches(CharSequence rawPassword, String encodedPassword) {
		long startedAt = System.nanoTime();
		try {
			return delegate.matches(rawPassword, encodedPassword);
		} finally {
			long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
			if (durationMs >= slowOperationThresholdMs) {
				LOGGER.warn("password_verification durationMs={}", durationMs);
			} else {
				LOGGER.info("password_verification durationMs={}", durationMs);
			}
		}
	}
}
