package pl.macieserafin.hourforge.auth;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {
    @EntityGraph(attributePaths = "user")
    Optional<UserCredential> findByUserEmailIgnoreCase(String email);
}
