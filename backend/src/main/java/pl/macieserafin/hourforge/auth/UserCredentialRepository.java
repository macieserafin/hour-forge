package pl.macieserafin.hourforge.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import pl.macieserafin.hourforge.user.EmailAddress;

import java.util.Optional;
import java.util.UUID;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {
    default Optional<UserCredential> findByUserEmailIgnoreCase(String email) {
        return findByNormalizedUserEmail(EmailAddress.normalize(email));
    }

    @EntityGraph(attributePaths = "user")
    @Query("select c from UserCredential c where lower(c.user.email) = lower(:email)")
    Optional<UserCredential> findByNormalizedUserEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from UserCredential c where c.userId = :userId")
    Optional<UserCredential> findLockedByUserId(UUID userId);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserCredential c where c.userId = :userId")
    int deleteByUserId(UUID userId);

    @Query("select c.sessionVersion from UserCredential c where c.userId = :userId")
    Optional<Integer> findSessionVersion(UUID userId);

    /** Reads address and version together, so a stale account snapshot cannot issue a valid link to its old address. */
    @Query("select c.sessionVersion from UserCredential c where c.userId = :userId and c.user.email = :email")
    Optional<Integer> findSessionVersionForEmail(UUID userId, String email);
}
