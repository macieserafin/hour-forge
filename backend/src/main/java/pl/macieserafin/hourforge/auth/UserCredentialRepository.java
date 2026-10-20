package pl.macieserafin.hourforge.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {
    @EntityGraph(attributePaths = "user")
    Optional<UserCredential> findByUserEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from UserCredential c where c.userId = :userId")
    Optional<UserCredential> findLockedByUserId(UUID userId);

    @Query("select c.sessionVersion from UserCredential c where c.userId = :userId")
    Optional<Integer> findSessionVersion(UUID userId);
}
