package com.pg_management.resident;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
		Map<String, ResidentRepository.RoomOccupancyProjection> occupancyByRoom = new HashMap<>();
		for (ResidentRepository.RoomOccupancyProjection occupancy :
				residentRepository.findRoomOccupancyByOwnerId(owner.getId(), ResidentStatus.ACTIVE)) {
			occupancyByRoom.put(normalizeRoomNumber(occupancy.getRoomNumber()), occupancy);
		}

		List<RoomResponse> rooms = new ArrayList<>();
		for (OwnerRoom room : ownerRoomRepository.findAllByOwner_IdOrderByRoomNumberAsc(owner.getId())) {
			ResidentRepository.RoomOccupancyProjection occupancy =
					occupancyByRoom.remove(normalizeRoomNumber(room.getRoomNumber()));
			rooms.add(RoomResponse.from(room, occupancy));
		}
		for (ResidentRepository.RoomOccupancyProjection occupancy : occupancyByRoom.values()) {
			long occupied = occupancy.getOccupied() == null ? 0 : occupancy.getOccupied();
			long residentCount = occupancy.getResidentCount() == null ? 0 : occupancy.getResidentCount();
			Integer capacity = occupancy.getSharing();
			rooms.add(new RoomResponse(
					occupancy.getRoomNumber(),
					capacity,
					occupied,
					residentCount,
					capacity == null ? 0 : Math.max(capacity - occupied, 0),
					false));
		}
		rooms.sort((first, second) -> first.roomNumber().compareToIgnoreCase(second.roomNumber()));
		return rooms;
	}

	public RoomResponse add(OwnerAccount owner, RoomRequest request) {
		String roomNumber = request.roomNumber().trim();
		if (ownerRoomRepository.findByOwner_IdAndRoomNumberIgnoreCase(owner.getId(), roomNumber).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "That room has already been added");
		}

		for (Integer existingSharing :
				residentRepository.findDistinctSharingByOwner_IdAndRoomNumberIgnoreCase(owner.getId(), roomNumber)) {
			if (existingSharing != null && !existingSharing.equals(request.sharing())) {
				throw new ResponseStatusException(
						HttpStatus.CONFLICT, "Room sharing must match the sharing already assigned to its residents");
			}
		}
		long activeResidents = residentRepository.countByOwner_IdAndRoomNumberIgnoreCaseAndStatus(
				owner.getId(), roomNumber, ResidentStatus.ACTIVE);
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
		boolean hasResidentRecords = residentRepository.existsByOwner_IdAndRoomNumberIgnoreCase(
				owner.getId(), room.getRoomNumber());
		if (hasResidentRecords) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT, "This room has resident records and cannot be deleted");
		}
		ownerRoomRepository.delete(room);
	}

	private String normalizeRoomNumber(String roomNumber) {
		return roomNumber.trim().toLowerCase(Locale.ROOT);
	}
}
