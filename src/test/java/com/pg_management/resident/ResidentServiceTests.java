package com.pg_management.resident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pg_management.resident.dto.UpdateResidentRequest;
import com.pg_management.resident.storage.PrivateDocumentStorageService;
import com.pg_management.security.OwnerAccount;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ResidentServiceTests {

	@Mock
	private ResidentRepository residentRepository;

	@Mock
	private PrivateDocumentStorageService documentStorage;

	private final OwnerAccount owner = mock(OwnerAccount.class);

	@InjectMocks
	private ResidentService residentService;

	@org.junit.jupiter.api.BeforeEach
	void configureOwner() {
		when(owner.getId()).thenReturn(3L);
	}

	@Test
	void updateChangesResidentDetails() {
		Resident resident = createResident();
		when(residentRepository.findByIdAndOwner_Id(7L, 3L)).thenReturn(Optional.of(resident));
		when(residentRepository.save(resident)).thenReturn(resident);
		UpdateResidentRequest request = new UpdateResidentRequest(
				"Updated Resident",
				"Updated Father",
				"+919876543210",
				"B-202",
				new BigDecimal("15000.00"),
				new BigDecimal("30000.00"),
				LocalDate.of(2026, 10, 5),
				"Updated permanent address",
				"Updated company address");

		var response = residentService.update(owner, 7L, request);

		assertEquals("Updated Resident", response.name());
		assertEquals("B-202", response.roomNumber());
		assertEquals(new BigDecimal("15000.00"), response.rent());
	}

	@Test
	void cannotFindResidentOutsideTheOwnersDirectory() {
		when(residentRepository.findByIdAndOwner_Id(7L, 3L)).thenReturn(Optional.empty());

		assertThrows(ResponseStatusException.class, () -> residentService.findById(owner, 7L));

		verify(residentRepository).findByIdAndOwner_Id(7L, 3L);
	}

	@Test
	void markingResidentVacatedPreservesTheRecord() {
		Resident resident = createResident();
		when(residentRepository.findByIdAndOwner_Id(7L, 3L)).thenReturn(Optional.of(resident));

		residentService.markVacated(owner, 7L);

		ArgumentCaptor<Resident> savedResident = ArgumentCaptor.forClass(Resident.class);
		verify(residentRepository).save(savedResident.capture());
		assertEquals(ResidentStatus.VACATED, savedResident.getValue().getStatus());
		assertEquals(LocalDate.now(), savedResident.getValue().getVacatedDate());
		verify(residentRepository, never()).deleteById(7L);
	}

	@Test
	void permanentlyDeletingResidentRemovesRecordAndDocuments() {
		Resident resident = createResident();
		resident.setPhotoStorageKey("photo-key.png");
		resident.setIdentityProofStorageKey("proof-key.pdf");
		when(residentRepository.findByIdAndOwner_Id(7L, 3L)).thenReturn(Optional.of(resident));

		residentService.permanentlyDelete(owner, 7L);

		verify(residentRepository).delete(resident);
		verify(documentStorage).delete("photo-key.png");
		verify(documentStorage).delete("proof-key.pdf");
	}

	@Test
	void findAllReturnsOnlyActiveResidents() {
		Resident resident = createResident();
		when(residentRepository.findByOwner_IdAndStatusOrderByRoomNumberAscNameAsc(3L, ResidentStatus.ACTIVE)).thenReturn(List.of(resident));

		var results = residentService.findAll(owner);

		assertEquals(1, results.size());
		assertEquals("Original Resident", results.getFirst().name());
		verify(residentRepository).findByOwner_IdAndStatusOrderByRoomNumberAscNameAsc(3L, ResidentStatus.ACTIVE);
	}

	@Test
	void searchMatchesResidentNameFatherNameOrRoomWithoutCaseSensitivity() {
		Resident resident = createResident();
		when(residentRepository.searchByResidentDetails(owner, "101"))
				.thenReturn(List.of(resident));

		var results = residentService.search(owner, " 101 ", null);

		assertEquals(1, results.size());
		assertEquals("A-101", results.getFirst().roomNumber());
		verify(residentRepository).searchByResidentDetails(owner, "101");
	}

	@Test
	void searchMatchesVacatedResidentsByNameOrRoom() {
		Resident resident = createResident();
		resident.markVacated();
		when(residentRepository.searchByResidentDetails(owner, "Original"))
				.thenReturn(List.of(resident));

		var results = residentService.search(owner, " Original ", null);

		assertEquals(1, results.size());
		assertEquals(ResidentStatus.VACATED, results.getFirst().status());
		verify(residentRepository).searchByResidentDetails(owner, "Original");
	}

	@Test
	void searchWithinStatusReturnsOnlyResidentsWithThatStatus() {
		Resident vacatedResident = createResident();
		vacatedResident.markVacated();
		when(residentRepository.searchByStatusAndResidentDetails(owner, ResidentStatus.VACATED, "101"))
				.thenReturn(List.of(vacatedResident));

		var results = residentService.search(owner, " 101 ", ResidentStatus.VACATED);

		assertEquals(1, results.size());
		assertEquals(ResidentStatus.VACATED, results.getFirst().status());
		verify(residentRepository).searchByStatusAndResidentDetails(owner, ResidentStatus.VACATED, "101");
	}

	@Test
	void findByStatusReturnsResidentsForTheRequestedStatus() {
		Resident resident = createResident();
		resident.markVacated();
		when(residentRepository.findByOwner_IdAndStatusOrderByRoomNumberAscNameAsc(3L, ResidentStatus.VACATED)).thenReturn(List.of(resident));

		var results = residentService.findByStatus(owner, ResidentStatus.VACATED);

		assertEquals(1, results.size());
		assertEquals(ResidentStatus.VACATED, results.getFirst().status());
		verify(residentRepository).findByOwner_IdAndStatusOrderByRoomNumberAscNameAsc(3L, ResidentStatus.VACATED);
	}

	@Test
	void findAllMembersSortsByNumericRoomNumber() {
		Resident room203 = createResident("203");
		Resident room101 = createResident("101");
		Resident room406 = createResident("406");
		Resident room202 = createResident("202");
		Resident room305 = createResident("305");
		when(residentRepository.findAllByOwner_IdOrderByRoomNumberAscNameAsc(3L))
				.thenReturn(List.of(room203, room406, room101, room305, room202));

		var results = residentService.findAllMembers(owner);

		assertEquals(List.of("101", "202", "203", "305", "406"),
				results.stream().map(response -> response.roomNumber()).toList());
	}

	private Resident createResident(String roomNumber) {
		return new Resident(
				"Resident " + roomNumber,
				"Father",
				"+919876543210",
				roomNumber,
				new BigDecimal("12000.00"),
				new BigDecimal("24000.00"),
				LocalDate.of(2026, 10, 1),
				"Permanent address",
				"Company address");
	}

	private Resident createResident() {
		return new Resident(
				"Original Resident",
				"Original Father",
				"+919876543210",
				"A-101",
				new BigDecimal("12000.00"),
				new BigDecimal("24000.00"),
				LocalDate.of(2026, 10, 1),
				"Original permanent address",
				"Original company address");
	}
}
