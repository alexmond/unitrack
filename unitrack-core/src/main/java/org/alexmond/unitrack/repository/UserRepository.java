package org.alexmond.unitrack.repository;

import org.alexmond.unitrack.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsername(String username);

	boolean existsByEmailIgnoreCase(String email);

}
