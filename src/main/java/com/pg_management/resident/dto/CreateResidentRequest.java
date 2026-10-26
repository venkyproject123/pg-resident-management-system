package com.pg_management.resident.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record CreateResidentRequest(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Size(max = 100) String fatherName,
		@NotBlank @Pattern(regexp = "^\\+?[0-9]{7,15}$") String mobileNumber,
		@NotBlank @Size(max = 30) String roomNumber,
		@NotNull @Min(1) @Max(4) Integer sharing,
		@NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal rent,
		@NotNull @DecimalMin("0.0") BigDecimal advanceAmount,
		@NotNull LocalDate joiningDate,
		@NotBlank @Size(max = 1000) String permanentAddress,
		@NotBlank @Size(max = 1000) String companyAddress) {
}
