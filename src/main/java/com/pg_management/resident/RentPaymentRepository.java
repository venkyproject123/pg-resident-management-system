package com.pg_management.resident;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RentPaymentRepository extends JpaRepository<RentPayment, Long> {

	List<RentPayment> findAllByResidentAndPeriodYearOrderByPeriodMonthAsc(Resident resident, int periodYear);

	List<RentPayment> findAllByResident_Owner_IdAndPeriodYearBetween(
			Long ownerId, int firstYear, int lastYear);

	List<RentPayment> findAllByResident_Owner_IdAndPeriodYearAndPeriodMonth(
			Long ownerId, int year, int month);

	@org.springframework.data.jpa.repository.Query("""
			select new com.pg_management.resident.MonthlyRentPaymentAmount(
				payment.resident.id, payment.paid, payment.amount)
			from RentPayment payment
			where payment.resident.owner.id = :ownerId
			  and payment.periodYear = :year
			  and payment.periodMonth = :month
			""")
	List<MonthlyRentPaymentAmount> findAmountsByOwnerAndPeriod(
			@org.springframework.data.repository.query.Param("ownerId") Long ownerId,
			@org.springframework.data.repository.query.Param("year") int year,
			@org.springframework.data.repository.query.Param("month") int month);

	Optional<RentPayment> findByResidentAndPeriodYearAndPeriodMonth(
			Resident resident, int periodYear, int periodMonth);

	void deleteAllByResident(Resident resident);
}
