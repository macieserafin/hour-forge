package pl.macieserafin.hourforge.preferences;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID> {
    @Modifying(flushAutomatically = true)
    @Query("delete from UserPreferences p where p.userId = :userId")
    int deleteByUserId(UUID userId);
}
