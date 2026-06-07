package org.alexmond.unitrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A local user account. */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String username;

	@Setter
	@Column(name = "display_name")
	private String displayName;

	@Setter
	private String email;

	@Setter
	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role = Role.USER;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	public User(String username, String displayName, String email, String passwordHash, Role role) {
		this.username = username;
		this.displayName = displayName;
		this.email = email;
		this.passwordHash = passwordHash;
		this.role = role;
	}

}
