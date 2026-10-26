package com.pg_management.resident;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.pg_management.security.OwnerAccount;

public interface ResidentRepository extends JpaRepository<Resident, Long> {

	List<Resident> findByOwner_IdAndStatusOrderByRoomNumberAscNameAsc(Long ownerId, ResidentStatus status);

	List<Resident> findAllByOwner_IdOrderByRoomNumberAscNameAsc(Long ownerId);

	List<Resident> findAllByOwner_IdAndRoomNumberIgnoreCase(Long ownerId, String roomNumber);

	java.util.Optional<Resident> findByIdAndOwner_Id(Long id, Long ownerId);

	@Query("""
			select resident from Resident resident
			where resident.owner = :owner
			  and (lower(resident.name) like lower(concat('%', :keyword, '%'))
			   or lower(resident.fatherName) like lower(concat('%', :keyword, '%'))
			   or lower(resident.roomNumber) like lower(concat('%', :keyword, '%'))
			  )
			order by resident.roomNumber, resident.name
			""")
	List<Resident> searchByResidentDetails(@Param("owner") OwnerAccount owner, @Param("keyword") String keyword);

	@Query("""
			select resident from Resident resident
			where resident.owner = :owner
			  and resident.status = :status
			  and (
			    lower(resident.name) like lower(concat('%', :keyword, '%'))
			    or lower(resident.fatherName) like lower(concat('%', :keyword, '%'))
			    or lower(resident.roomNumber) like lower(concat('%', :keyword, '%'))
			  )
			order by resident.roomNumber, resident.name
			""")
	List<Resident> searchByStatusAndResidentDetails(
			@Param("owner") OwnerAccount owner,
			@Param("status") ResidentStatus status,
			@Param("keyword") String keyword);
}
