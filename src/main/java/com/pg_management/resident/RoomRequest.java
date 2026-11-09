package com.pg_management.resident;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoomRequest(
		@NotBlank @Size(max = 30) String roomNumber,
		@NotNull @Min(1) @Max(4) Integer sharing) {
}
