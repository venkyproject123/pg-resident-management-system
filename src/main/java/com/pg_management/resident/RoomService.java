package com.pg_management.resident;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.pg_management.security.OwnerAccount;

@Service
@Transactional
public class RoomService {

	private final OwnerRoomRepository ownerRoomRepository;
	private final ResidentRepository residentRepository;

	public RoomService(OwnerRoomRepository ownerRoomRepository, ResidentRepository residentRepository) {
		this.ownerRoomRepository = ownerRoomRepository;
		this.residentRepository = residentRepository;
	}

	@Transactional(readOnly = true)
	public List<RoomResponse> findAll(OwnerAccount owner) {
		return ownerRoomRepository.findAllByOwner_IdOrderByRoomNumberAsc(owner.getId()).stream()
				.map(RoomResponse::from)
				.toList();
	}

	public RoomResponse add(OwnerAccount owner, RoomRequest request) {
		String roomNumber = request.roomNumber().trim();
		if (ownerRoomRepository.findByOwner_IdAndRoomNumberIgnoreCase(owner.getId(), roomNumber).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "That room has already been added");
		}

		long activeResidents = 0;
		for (Resident resident : residentRepository.findAllByOwner_IdAndRoomNumberIgnoreCase(owner.getId(), roomNumber)) {
			if (resident.getSharing() != null && !resident.getSharing().equals(request.sharing())) {
				throw new ResponseStatusException(
						HttpStatus.CONFLICT, "Room sharing must match the sharing already assigned to its residents");
			}
			if (resident.getStatus() == ResidentStatus.ACTIVE) {
				activeResidents++;
			}
		}
		if (activeResidents > request.sharing()) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT, "Room capacity cannot be less than its active resident count");
		}

		OwnerRoom room = ownerRoomRepository.saveAndFlush(new OwnerRoom(owner, roomNumber, request.sharing()));
		return RoomResponse.from(room);
	}

	public void delete(OwnerAccount owner, String roomNumber) {
		OwnerRoom room = ownerRoomRepository.findByOwner_IdAndRoomNumberIgnoreCase(
				owner.getId(), roomNumber.trim())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
		boolean hasResidentRecords = !residentRepository.findAllByOwner_IdAndRoomNumberIgnoreCase(
				owner.getId(), room.getRoomNumber()).isEmpty();
		if (hasResidentRecords) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT, "This room has resident records and cannot be deleted");
		}
		ownerRoomRepository.delete(room);
	}
}
