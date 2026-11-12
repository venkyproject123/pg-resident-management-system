package com.pg_management.resident;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.pg_management.resident.dto.CreateResidentRequest;
import com.pg_management.resident.dto.ResidentResponse;
import com.pg_management.resident.dto.UpdateResidentRequest;
import com.pg_management.resident.storage.PrivateDocumentStorageService;
import com.pg_management.resident.storage.ResidentDocument;
import com.pg_management.resident.storage.ResidentDocumentType;
import com.pg_management.security.OwnerAccount;

@Service
@Transactional
public class ResidentService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ResidentService.class);

	private final ResidentRepository residentRepository;
	private final OwnerRoomRepository ownerRoomRepository;
	private final RentPaymentRepository rentPaymentRepository;
	private final PrivateDocumentStorageService documentStorage;

	public ResidentService(
			ResidentRepository residentRepository,
			OwnerRoomRepository ownerRoomRepository,
			RentPaymentRepository rentPaymentRepository,
			PrivateDocumentStorageService documentStorage) {
		this.residentRepository = residentRepository;
		this.ownerRoomRepository = ownerRoomRepository;
		this.rentPaymentRepository = rentPaymentRepository;
		this.documentStorage = documentStorage;
	}

	public ResidentResponse create(CreateResidentRequest request, OwnerAccount owner) {
		long startedAt = System.nanoTime();
		String roomNumber = request.roomNumber().trim();
		long validationStartedAt = System.nanoTime();
		validateRoomCapacity(owner, roomNumber, request.sharing(), null, true);
		long validationDurationMs = elapsedMs(validationStartedAt);
		Resident resident = new Resident(
				owner,
				request.name(),
				request.fatherName(),
				request.mobileNumber(),
				roomNumber,
				request.sharing(),
				request.rent(),
				request.advanceAmount(),
				request.joiningDate(),
				request.permanentAddress(),
				request.companyAddress());

		long saveStartedAt = System.nanoTime();
		Resident savedResident = residentRepository.save(resident);
		LOGGER.info("resident_create ownerId={} residentId={} roomValidationMs={} saveMs={} serviceDurationMs={}",
				owner.getId(), savedResident.getId(), validationDurationMs, elapsedMs(saveStartedAt), elapsedMs(startedAt));
		return ResidentResponse.from(savedResident);
	}

	@Transactional(readOnly = true)
	public List<ResidentResponse> findAll(OwnerAccount owner) {
		return roomOrderedResponses(residentRepository.findByOwner_IdAndStatusOrderByRoomNumberAscNameAsc(
				owner.getId(), ResidentStatus.ACTIVE));
	}

	@Transactional(readOnly = true)
	public List<ResidentResponse> findAllMembers(OwnerAccount owner) {
		return roomOrderedResponses(residentRepository.findAllByOwner_IdOrderByRoomNumberAscNameAsc(owner.getId()));
	}

	@Transactional(readOnly = true)
	public List<ResidentResponse> findByStatus(OwnerAccount owner, ResidentStatus status) {
		return roomOrderedResponses(residentRepository.findByOwner_IdAndStatusOrderByRoomNumberAscNameAsc(
				owner.getId(), status));
	}

	@Transactional(readOnly = true)
	public List<ResidentResponse> search(OwnerAccount owner, String keyword, ResidentStatus status) {
		String searchTerm = keyword.trim();
		List<Resident> matches = status == null
				? residentRepository.searchByResidentDetails(owner, searchTerm)
				: residentRepository.searchByStatusAndResidentDetails(owner, status, searchTerm);
		return roomOrderedResponses(matches);
	}

	@Transactional(readOnly = true)
	public ResidentPageResponse findPage(
			OwnerAccount owner, int page, int size, String keyword, ResidentStatus status) {
		String searchTerm = keyword == null || keyword.isBlank() ? null : keyword.trim();
		Page<Resident> residents = residentRepository.searchPageByOwnerId(
				owner.getId(),
				status,
				searchTerm,
				PageRequest.of(page, size, Sort.by(
						Sort.Order.asc("roomNumber"),
						Sort.Order.asc("name"),
						Sort.Order.asc("id"))));
		return new ResidentPageResponse(
				residents.getContent().stream().map(ResidentResponse::from).toList(),
				residents.getNumber(),
				residents.getSize(),
				residents.getTotalElements(),
				residents.getTotalPages(),
				residents.isFirst(),
				residents.isLast());
	}

	private List<ResidentResponse> roomOrderedResponses(List<Resident> residents) {
		return residents.stream()
				.sorted(Comparator.comparing(Resident::getRoomNumber, ResidentService::compareRoomNumbers)
						.thenComparing(Resident::getName, String.CASE_INSENSITIVE_ORDER))
				.map(ResidentResponse::from)
				.toList();
	}

	private static int compareRoomNumbers(String first, String second) {
		boolean firstNumeric = first.matches("\\d+");
		boolean secondNumeric = second.matches("\\d+");
		if (firstNumeric && secondNumeric) {
			return new BigInteger(first).compareTo(new BigInteger(second));
		}
		if (firstNumeric != secondNumeric) {
			return firstNumeric ? -1 : 1;
		}
		return first.compareToIgnoreCase(second);
	}

	@Transactional(readOnly = true)
	public ResidentResponse findById(OwnerAccount owner, Long id) {
		return ResidentResponse.from(getResident(owner, id));
	}

	public ResidentResponse update(OwnerAccount owner, Long id, UpdateResidentRequest request) {
		Resident resident = getResident(owner, id);
		String roomNumber = request.roomNumber().trim();
		validateRoomCapacity(owner, roomNumber, request.sharing(), id, resident.getStatus() == ResidentStatus.ACTIVE);
		resident.updateDetails(
				request.name(),
				request.fatherName(),
				request.mobileNumber(),
				roomNumber,
				request.sharing(),
				request.rent(),
				request.advanceAmount(),
				request.joiningDate(),
				request.permanentAddress(),
				request.companyAddress());
		return ResidentResponse.from(residentRepository.save(resident));
	}

	private void validateRoomCapacity(
			OwnerAccount owner, String roomNumber, Integer sharing, Long currentResidentId, boolean currentlyActive) {
		ownerRoomRepository.findByOwner_IdAndRoomNumberIgnoreCase(owner.getId(), roomNumber)
				.filter(room -> room.getSharing() != sharing)
				.ifPresent(room -> {
					throw new ResponseStatusException(
							HttpStatus.CONFLICT, "Resident sharing must match the room's configured sharing capacity");
				});
		for (Integer existingSharing : residentRepository.findDistinctSharingByOwner_IdAndRoomNumberIgnoreCase(
				owner.getId(), roomNumber)) {
			if (existingSharing != null && !existingSharing.equals(sharing)) {
				throw new ResponseStatusException(
						HttpStatus.CONFLICT, "All residents in a room must use the same sharing capacity");
			}
		}
		long activeResidents = currentResidentId == null
				? residentRepository.countByOwner_IdAndRoomNumberIgnoreCaseAndStatus(
						owner.getId(), roomNumber, ResidentStatus.ACTIVE)
				: residentRepository.countByOwner_IdAndRoomNumberIgnoreCaseAndStatusAndIdNot(
						owner.getId(), roomNumber, ResidentStatus.ACTIVE, currentResidentId);
		if (currentlyActive) {
			activeResidents++;
		}
		if (activeResidents > sharing) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT, "This room is already full for the selected sharing capacity");
		}
	}

	public void markVacated(OwnerAccount owner, Long id) {
		Resident resident = getResident(owner, id);
		resident.markVacated();
		residentRepository.save(resident);
	}

	public void permanentlyDelete(OwnerAccount owner, Long id) {
		long startedAt = System.nanoTime();
		Resident resident = getResident(owner, id);
		long paymentCleanupStartedAt = System.nanoTime();
		rentPaymentRepository.deleteAllByResident(resident);
		long paymentCleanupDurationMs = elapsedMs(paymentCleanupStartedAt);
		long residentDeleteStartedAt = System.nanoTime();
		residentRepository.delete(resident);
		long residentDeleteDurationMs = elapsedMs(residentDeleteStartedAt);
		registerPermanentDeletionCleanup(resident.getPhotoStorageKey(), resident.getIdentityProofStorageKey());
		LOGGER.info("resident_permanent_delete ownerId={} residentId={} paymentCleanupMs={} residentDeleteMs={} serviceDurationMs={}",
				owner.getId(), id, paymentCleanupDurationMs, residentDeleteDurationMs, elapsedMs(startedAt));
	}

	private long elapsedMs(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}

	public void uploadPhoto(OwnerAccount owner, Long id, MultipartFile file) {
		uploadDocument(owner, id, file, ResidentDocumentType.PHOTO);
	}

	public void uploadIdentityProof(OwnerAccount owner, Long id, MultipartFile file) {
		uploadDocument(owner, id, file, ResidentDocumentType.IDENTITY_PROOF);
	}

	public ResidentDocument getPhoto(OwnerAccount owner, Long id) {
		Resident resident = getResident(owner, id);
		if (resident.getPhotoStorageKey() == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resident photo not found");
		}
		return documentStorage.load(resident.getPhotoStorageKey());
	}

	public ResidentDocument getIdentityProof(OwnerAccount owner, Long id) {
		Resident resident = getResident(owner, id);
		if (resident.getIdentityProofStorageKey() == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resident identity proof not found");
		}
		return documentStorage.load(resident.getIdentityProofStorageKey());
	}

	private void uploadDocument(OwnerAccount owner, Long id, MultipartFile file, ResidentDocumentType documentType) {
		Resident resident = getResident(owner, id);
		String previousStorageKey = documentType == ResidentDocumentType.PHOTO
				? resident.getPhotoStorageKey()
				: resident.getIdentityProofStorageKey();
		String newStorageKey = documentStorage.store(file, documentType);

		if (documentType == ResidentDocumentType.PHOTO) {
			resident.setPhotoStorageKey(newStorageKey);
		} else {
			resident.setIdentityProofStorageKey(newStorageKey);
		}
		try {
			residentRepository.save(resident);
		} catch (RuntimeException exception) {
			cleanupDocument(newStorageKey);
			throw exception;
		}
		registerDocumentCleanup(newStorageKey, previousStorageKey);
	}

	private void registerDocumentCleanup(String newStorageKey, String previousStorageKey) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			if (previousStorageKey != null) {
				cleanupDocument(previousStorageKey);
			}
			return;
		}

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status == STATUS_COMMITTED) {
					if (previousStorageKey != null) {
						cleanupDocument(previousStorageKey);
					}
				} else {
					cleanupDocument(newStorageKey);
				}
			}
		});
	}

	private void registerPermanentDeletionCleanup(String... storageKeys) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			for (String storageKey : storageKeys) {
				if (storageKey != null) {
					cleanupDocument(storageKey);
				}
			}
			return;
		}

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status == STATUS_COMMITTED) {
					for (String storageKey : storageKeys) {
						if (storageKey != null) {
							cleanupDocument(storageKey);
						}
					}
				}
			}
		});
	}

	private void cleanupDocument(String storageKey) {
		try {
			documentStorage.delete(storageKey);
		} catch (ResponseStatusException exception) {
			LOGGER.warn("Could not clean up a replaced resident document");
		}
	}

	private Resident getResident(OwnerAccount owner, Long id) {
		return residentRepository.findByIdAndOwner_Id(id, owner.getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resident not found"));
	}
}
