package com.pg_management.resident;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerRoomRepository extends JpaRepository<OwnerRoom, Long> {

	List<OwnerRoom> findAllByOwner_IdOrderByRoomNumberAsc(Long ownerId);

	Optional<OwnerRoom> findByOwner_IdAndRoomNumberIgnoreCase(Long ownerId, String roomNumber);
}
