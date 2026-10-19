package com.pg_management.resident.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.pg_management.resident.Resident;
import com.pg_management.resident.ResidentStatus;

public record ResidentResponse(
		Long id,
		String name,
		String fatherName,
		String mobileNumber,
		String roomNumber,
		BigDecimal rent,
		BigDecimal advanceAmount,
		LocalDate joiningDate,
		LocalDate vacatedDate,
		String permanentAddress,
		String companyAddress,
		ResidentStatus status,
		boolean hasPhoto,
		boolean hasIdentityProof) {

	public static ResidentResponse from(Resident resident) {
		return new ResidentResponse(
				resident.getId(),
				resident.getName(),
				resident.getFatherName(),
				resident.getMobileNumber(),
				resident.getRoomNumber(),
				resident.getRent(),
				resident.getAdvanceAmount(),
				resident.getJoiningDate(),
				resident.getVacatedDate(),
				resident.getPermanentAddress(),
				resident.getCompanyAddress(),
				resident.getStatus(),
				resident.getPhotoStorageKey() != null,
				resident.getIdentityProofStorageKey() != null);
	}
}
