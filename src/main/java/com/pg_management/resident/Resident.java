package com.pg_management.resident;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import com.pg_management.security.OwnerAccount;

@Entity
@Table(name = "residents")
public class Resident {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private OwnerAccount owner;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "father_name", nullable = false, length = 100)
	private String fatherName;

	@Column(name = "mobile_number", length = 20)
	private String mobileNumber;

	@Column(name = "room_number", nullable = false, length = 30)
	private String roomNumber;

	@Column(name = "sharing_capacity")
	private Integer sharing;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal rent;

	@Column(name = "advance_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal advanceAmount;

	@Column(name = "joining_date", nullable = false)
	private LocalDate joiningDate;

	@Column(name = "vacated_date")
	private LocalDate vacatedDate;

	@Column(name = "permanent_address", nullable = false, length = 1000)
	private String permanentAddress;

	@Column(name = "company_address", nullable = false, length = 1000)
	private String companyAddress;

	@Column(name = "photo_storage_key", length = 50)
	private String photoStorageKey;

	@Column(name = "identity_proof_storage_key", length = 50)
	private String identityProofStorageKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ResidentStatus status = ResidentStatus.ACTIVE;

	protected Resident() {
	}

	public Resident(String name, String fatherName, String mobileNumber, String roomNumber, BigDecimal rent, BigDecimal advanceAmount,
			LocalDate joiningDate, String permanentAddress, String companyAddress) {
		this(name, fatherName, mobileNumber, roomNumber, null, rent, advanceAmount, joiningDate, permanentAddress, companyAddress);
	}

	public Resident(String name, String fatherName, String mobileNumber, String roomNumber, Integer sharing, BigDecimal rent,
			BigDecimal advanceAmount, LocalDate joiningDate, String permanentAddress, String companyAddress) {
		updateDetails(name, fatherName, mobileNumber, roomNumber, sharing, rent, advanceAmount, joiningDate, permanentAddress, companyAddress);
	}

	public Resident(OwnerAccount owner, String name, String fatherName, String mobileNumber, String roomNumber, BigDecimal rent,
			BigDecimal advanceAmount, LocalDate joiningDate, String permanentAddress, String companyAddress) {
		this(owner, name, fatherName, mobileNumber, roomNumber, null, rent, advanceAmount, joiningDate, permanentAddress, companyAddress);
	}

	public Resident(OwnerAccount owner, String name, String fatherName, String mobileNumber, String roomNumber, Integer sharing, BigDecimal rent,
			BigDecimal advanceAmount, LocalDate joiningDate, String permanentAddress, String companyAddress) {
		this(name, fatherName, mobileNumber, roomNumber, sharing, rent, advanceAmount, joiningDate, permanentAddress, companyAddress);
		this.owner = owner;
	}

	public OwnerAccount getOwner() {
		return owner;
	}

	public void updateDetails(String name, String fatherName, String mobileNumber, String roomNumber, BigDecimal rent,
			BigDecimal advanceAmount, LocalDate joiningDate, String permanentAddress, String companyAddress) {
		updateDetails(name, fatherName, mobileNumber, roomNumber, sharing, rent, advanceAmount, joiningDate, permanentAddress, companyAddress);
	}

	public void updateDetails(String name, String fatherName, String mobileNumber, String roomNumber, Integer sharing,
			BigDecimal rent, BigDecimal advanceAmount, LocalDate joiningDate, String permanentAddress, String companyAddress) {
		this.name = name;
		this.fatherName = fatherName;
		this.mobileNumber = mobileNumber;
		this.roomNumber = roomNumber;
		this.sharing = sharing;
		this.rent = rent;
		this.advanceAmount = advanceAmount;
		this.joiningDate = joiningDate;
		this.permanentAddress = permanentAddress;
		this.companyAddress = companyAddress;
	}

	public void markVacated() {
		if (status != ResidentStatus.VACATED) {
			this.vacatedDate = LocalDate.now();
		}
		this.status = ResidentStatus.VACATED;
	}

	public void setPhotoStorageKey(String photoStorageKey) {
		this.photoStorageKey = photoStorageKey;
	}

	public void setIdentityProofStorageKey(String identityProofStorageKey) {
		this.identityProofStorageKey = identityProofStorageKey;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getFatherName() {
		return fatherName;
	}

	public String getMobileNumber() {
		return mobileNumber;
	}

	public String getRoomNumber() {
		return roomNumber;
	}

	public Integer getSharing() {
		return sharing;
	}

	public BigDecimal getRent() {
		return rent;
	}

	public BigDecimal getAdvanceAmount() {
		return advanceAmount;
	}

	public LocalDate getJoiningDate() {
		return joiningDate;
	}

	public LocalDate getVacatedDate() {
		return vacatedDate;
	}

	public String getPermanentAddress() {
		return permanentAddress;
	}

	public String getCompanyAddress() {
		return companyAddress;
	}

	public ResidentStatus getStatus() {
		return status;
	}

	public String getPhotoStorageKey() {
		return photoStorageKey;
	}

	public String getIdentityProofStorageKey() {
		return identityProofStorageKey;
	}
}
