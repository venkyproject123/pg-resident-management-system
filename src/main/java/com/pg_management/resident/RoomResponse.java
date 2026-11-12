package com.pg_management.resident;

public record RoomResponse(
		String roomNumber,
		Integer sharing,
		long occupied,
		long residentCount,
		long availableBeds,
		boolean configured) {

	public RoomResponse(String roomNumber, int sharing) {
		this(roomNumber, sharing, 0, 0, sharing, true);
	}

	public static RoomResponse from(OwnerRoom room) {
		return new RoomResponse(room.getRoomNumber(), room.getSharing(), 0, 0, room.getSharing(), true);
	}

	public static RoomResponse from(OwnerRoom room, ResidentRepository.RoomOccupancyProjection occupancy) {
		long occupied = occupancy == null || occupancy.getOccupied() == null ? 0 : occupancy.getOccupied();
		long residentCount = occupancy == null || occupancy.getResidentCount() == null
				? 0 : occupancy.getResidentCount();
		return new RoomResponse(
				room.getRoomNumber(),
				room.getSharing(),
				occupied,
				residentCount,
				Math.max(room.getSharing() - occupied, 0),
				true);
	}
}
