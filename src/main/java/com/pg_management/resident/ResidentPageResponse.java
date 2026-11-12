package com.pg_management.resident;

import java.util.List;

import com.pg_management.resident.dto.ResidentResponse;

public record ResidentPageResponse(
		List<ResidentResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last) {
}
