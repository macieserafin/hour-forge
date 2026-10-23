package pl.macieserafin.hourforge.twofactor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserTwoFactorRepository extends JpaRepository<UserTwoFactor, UUID> {
    @Query(value = "select * from user_two_factor where user_id = :userId for update", nativeQuery = true)
    Optional<UserTwoFactor> findLocked(UUID userId);

    @Query("select count(t) > 0 from UserTwoFactor t where t.userId = :userId and t.enabledAt is not null")
    boolean isEnabled(UUID userId);
}
