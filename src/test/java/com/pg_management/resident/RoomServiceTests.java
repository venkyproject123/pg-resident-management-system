package com.pg_management.resident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.pg_management.security.OwnerAccount;

@ExtendWith(MockitoExtension.class)
class RoomServiceTests {

	@Mock
	private OwnerRoomRepository ownerRoomRepository;

	@Mock
	private ResidentRepository residentRepository;

	@Mock
	private OwnerAccount owner;

	@InjectMocks
	private RoomService roomService;

	@Test
	void addsRoomWithoutResidents() {
		when(owner.getId()).thenReturn(7L);
		when(ownerRoomRepository.findByOwner_IdAndRoomNumberIgnoreCase(7L, "101")).thenReturn(Optional.empty());
		when(residentRepository.findAllByOwner_IdAndRoomNumberIgnoreCase(7L, "101")).thenReturn(List.of());
		when(ownerRoomRepository.saveAndFlush(any(OwnerRoom.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		RoomResponse response = roomService.add(owner, new RoomRequest(" 101 ", 3));

		assertEquals(new RoomResponse("101", 3), response);
		verify(ownerRoomRepository).saveAndFlush(any(OwnerRoom.class));
	}

	@Test
	void rejectsDuplicateRoomNumber() {
		when(owner.getId()).thenReturn(7L);
		when(ownerRoomRepository.findByOwner_IdAndRoomNumberIgnoreCase(7L, "101"))
				.thenReturn(Optional.of(new OwnerRoom(owner, "101", 2)));

		ResponseStatusException exception = assertThrows(
				ResponseStatusException.class,
				() -> roomService.add(owner, new RoomRequest("101", 2)));

		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
	}

	@Test
	void rejectsRoomCapacityBelowExistingActiveResidents() {
		when(owner.getId()).thenReturn(7L);
		when(ownerRoomRepository.findByOwner_IdAndRoomNumberIgnoreCase(7L, "101")).thenReturn(Optional.empty());
		Resident firstResident = resident();
		Resident secondResident = resident();
		when(residentRepository.findAllByOwner_IdAndRoomNumberIgnoreCase(7L, "101"))
				.thenReturn(List.of(firstResident, secondResident));

		ResponseStatusException exception = assertThrows(
				ResponseStatusException.class,
				() -> roomService.add(owner, new RoomRequest("101", 1)));

		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
	}

	@Test
	void deletesRoomWhenNoActiveResidentsOccupyIt() {
		when(owner.getId()).thenReturn(7L);
		OwnerRoom room = new OwnerRoom(owner, "101", 2);
		when(ownerRoomRepository.findByOwner_IdAndRoomNumberIgnoreCase(7L, "101"))
				.thenReturn(Optional.of(room));
		when(residentRepository.findAllByOwner_IdAndRoomNumberIgnoreCase(7L, "101"))
				.thenReturn(List.of());

		roomService.delete(owner, " 101 ");

		verify(ownerRoomRepository).delete(room);
	}

	@Test
	void preventsDeletingRoomWithActiveResidents() {
		when(owner.getId()).thenReturn(7L);
		when(ownerRoomRepository.findByOwner_IdAndRoomNumberIgnoreCase(7L, "101"))
				.thenReturn(Optional.of(new OwnerRoom(owner, "101", 2)));
		when(residentRepository.findAllByOwner_IdAndRoomNumberIgnoreCase(7L, "101"))
				.thenReturn(List.of(resident()));

		ResponseStatusException exception = assertThrows(
				ResponseStatusException.class,
				() -> roomService.delete(owner, "101"));

		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
	}

	private Resident resident() {
		return new Resident(owner, "Resident", "Parent", null, "101", 2,
				java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO,
				java.time.LocalDate.of(2026, 1, 1), "Address", "Company");
	}
}
