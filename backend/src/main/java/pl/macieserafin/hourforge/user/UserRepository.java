package pl.macieserafin.hourforge.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);

    // Serialize a user's timer operations without blocking FK key-share locks on this user.
    @Query(value = "select * from users where id = :id for no key update", nativeQuery = true)
    Optional<User> findLockedForTimeEntryById(UUID id);
}
