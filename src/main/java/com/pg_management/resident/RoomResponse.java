package com.pg_management.resident;

public record RoomResponse(String roomNumber, int sharing) {

	public static RoomResponse from(OwnerRoom room) {
		return new RoomResponse(room.getRoomNumber(), room.getSharing());
	}
}
