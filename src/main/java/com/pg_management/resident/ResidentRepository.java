package com.pg_management.resident;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.pg_management.security.OwnerAccount;

public interface ResidentRepository extends JpaRepository<Resident, Long> {

	List<Resident> findByOwner_IdAndStatusOrderByRoomNumberAscNameAsc(Long ownerId, ResidentStatus status);

	List<Resident> findAllByOwner_IdOrderByRoomNumberAscNameAsc(Long ownerId);

	List<Resident> findAllByOwner_IdAndRoomNumberIgnoreCase(Long ownerId, String roomNumber);

	List<Integer> findDistinctSharingByOwner_IdAndRoomNumberIgnoreCase(Long ownerId, String roomNumber);

	long countByOwner_IdAndRoomNumberIgnoreCaseAndStatus(
			Long ownerId, String roomNumber, ResidentStatus status);

	long countByOwner_IdAndRoomNumberIgnoreCaseAndStatusAndIdNot(
			Long ownerId, String roomNumber, ResidentStatus status, Long excludedResidentId);

	java.util.Optional<Resident> findByIdAndOwner_Id(Long id, Long ownerId);

	long countByOwner_Id(Long ownerId);

	long countByOwner_IdAndStatus(Long ownerId, ResidentStatus status);

	boolean existsByOwner_IdAndRoomNumberIgnoreCase(Long ownerId, String roomNumber);

	@Query(value = """
			select resident from Resident resident
			where resident.owner.id = :ownerId
			  and (:status is null or resident.status = :status)
			  and (:keyword is null
			   or lower(resident.name) like lower(concat('%', :keyword, '%'))
			   or lower(resident.fatherName) like lower(concat('%', :keyword, '%'))
			   or lower(resident.roomNumber) like lower(concat('%', :keyword, '%')))
			""", countQuery = """
			select count(resident) from Resident resident
			where resident.owner.id = :ownerId
			  and (:status is null or resident.status = :status)
			  and (:keyword is null
			   or lower(resident.name) like lower(concat('%', :keyword, '%'))
			   or lower(resident.fatherName) like lower(concat('%', :keyword, '%'))
			   or lower(resident.roomNumber) like lower(concat('%', :keyword, '%')))
			""")
	Page<Resident> searchPageByOwnerId(
			@Param("ownerId") Long ownerId,
			@Param("status") ResidentStatus status,
			@Param("keyword") String keyword,
			Pageable pageable);

	@Query("select min(resident.joiningDate) from Resident resident where resident.owner.id = :ownerId")
	Optional<LocalDate> findEarliestJoiningDateByOwnerId(@Param("ownerId") Long ownerId);

	@Query("""
			select new com.pg_management.resident.MonthlyRentResident(
				resident.id, resident.name, resident.roomNumber, resident.sharing,
				resident.mobileNumber, resident.rent, resident.joiningDate)
			from Resident resident
			where resident.owner.id = :ownerId
			  and resident.joiningDate <= :monthEnd
			  and (resident.status <> :vacatedStatus
			   or resident.vacatedDate is null
			   or resident.vacatedDate >= :monthStart)
			order by resident.roomNumber, resident.name
			""")
	List<MonthlyRentResident> findMonthlyRentResidents(
			@Param("ownerId") Long ownerId,
			@Param("monthStart") LocalDate monthStart,
			@Param("monthEnd") LocalDate monthEnd,
			@Param("vacatedStatus") ResidentStatus vacatedStatus);

	@Query("""
			select resident.roomNumber as roomNumber,
				max(resident.sharing) as sharing,
				sum(case when resident.status = :activeStatus then 1 else 0 end) as occupied,
				count(resident.id) as residentCount
			from Resident resident
			where resident.owner.id = :ownerId
			group by resident.roomNumber
			""")
	List<RoomOccupancyProjection> findRoomOccupancyByOwnerId(
			@Param("ownerId") Long ownerId,
			@Param("activeStatus") ResidentStatus activeStatus);

	interface RoomOccupancyProjection {
		String getRoomNumber();
		Integer getSharing();
		Long getOccupied();
		Long getResidentCount();
	}

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
