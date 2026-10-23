package com.pg_management.resident;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RentPaymentRepository extends JpaRepository<RentPayment, Long> {

	List<RentPayment> findAllByResidentAndPeriodYearOrderByPeriodMonthAsc(Resident resident, int periodYear);

	Optional<RentPayment> findByResidentAndPeriodYearAndPeriodMonth(
			Resident resident, int periodYear, int periodMonth);

	void deleteAllByResident(Resident resident);
}
