package com.pg_management.resident;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.http.HttpStatus;
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
	private final RentPaymentRepository rentPaymentRepository;
	private final PrivateDocumentStorageService documentStorage;

	public ResidentService(
			ResidentRepository residentRepository,
			RentPaymentRepository rentPaymentRepository,
			PrivateDocumentStorageService documentStorage) {
		this.residentRepository = residentRepository;
		this.rentPaymentRepository = rentPaymentRepository;
		this.documentStorage = documentStorage;
	}

	public ResidentResponse create(CreateResidentRequest request, OwnerAccount owner) {
		String roomNumber = request.roomNumber().trim();
		validateRoomCapacity(owner, roomNumber, request.sharing(), null, true);
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

		return ResidentResponse.from(residentRepository.save(resident));
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
		List<Resident> roomResidents = residentRepository.findAllByOwner_IdAndRoomNumberIgnoreCase(
				owner.getId(), roomNumber);
		long activeResidents = 0;
		for (Resident roomResident : roomResidents) {
			if (roomResident.getId() != null && roomResident.getId().equals(currentResidentId)) {
				continue;
			}
			if (roomResident.getSharing() != null && !roomResident.getSharing().equals(sharing)) {
				throw new ResponseStatusException(
						HttpStatus.CONFLICT, "All residents in a room must use the same sharing capacity");
			}
			if (roomResident.getStatus() == ResidentStatus.ACTIVE) {
				activeResidents++;
			}
		}
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
		Resident resident = getResident(owner, id);
		rentPaymentRepository.deleteAllByResident(resident);
		residentRepository.delete(resident);
		registerPermanentDeletionCleanup(resident.getPhotoStorageKey(), resident.getIdentityProofStorageKey());
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
