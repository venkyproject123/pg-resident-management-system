package com.pg_management.observability;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ApiRequestTimingFilter extends OncePerRequestFilter {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApiRequestTimingFilter.class);

	private final long slowRequestThresholdMs;

	public ApiRequestTimingFilter(long slowRequestThresholdMs) {
		this.slowRequestThresholdMs = slowRequestThresholdMs;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		long startedAt = System.nanoTime();
		try {
			filterChain.doFilter(request, response);
		} finally {
			if (request.getRequestURI().startsWith("/api/") && !"OPTIONS".equalsIgnoreCase(request.getMethod())) {
				long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
				if (durationMs >= slowRequestThresholdMs || response.getStatus() >= 500) {
					LOGGER.warn("api_request method={} path={} status={} durationMs={}",
							request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
				} else {
					LOGGER.info("api_request method={} path={} status={} durationMs={}",
							request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
				}
			}
		}
	}
}
