package com.pg_management.resident;

import com.pg_management.security.OwnerAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "owner_rooms", uniqueConstraints = {
		@UniqueConstraint(name = "uk_owner_rooms_owner_number", columnNames = { "owner_id", "room_number" })
})
public class OwnerRoom {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private OwnerAccount owner;

	@Column(name = "room_number", nullable = false, length = 30)
	private String roomNumber;

	@Column(name = "sharing_capacity", nullable = false)
	private int sharing;

	protected OwnerRoom() {
	}

	public OwnerRoom(OwnerAccount owner, String roomNumber, int sharing) {
		this.owner = owner;
		this.roomNumber = roomNumber;
		this.sharing = sharing;
	}

	public Long getId() {
		return id;
	}

	public String getRoomNumber() {
		return roomNumber;
	}

	public int getSharing() {
		return sharing;
	}
}
