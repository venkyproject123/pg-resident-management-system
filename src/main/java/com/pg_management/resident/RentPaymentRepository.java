package com.pg_management.resident;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RentPaymentRepository extends JpaRepository<RentPayment, Long> {

	List<RentPayment> findAllByResidentAndPeriodYearOrderByPeriodMonthAsc(Resident resident, int periodYear);

	List<RentPayment> findAllByResident_Owner_IdAndPeriodYearBetween(
			Long ownerId, int firstYear, int lastYear);

	Optional<RentPayment> findByResidentAndPeriodYearAndPeriodMonth(
			Resident resident, int periodYear, int periodMonth);

	void deleteAllByResident(Resident resident);
}
