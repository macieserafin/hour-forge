package pl.macieserafin.hourforge.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {
    /** The only way to find an account from a provider: by its subject, compared exactly. */
    @Query("select i from UserIdentity i join fetch i.user where i.provider = :provider and i.subject = :subject")
    Optional<UserIdentity> findByProviderAndSubject(IdentityProvider provider, String subject);

    @Query("select i from UserIdentity i where i.user.id = :userId and i.provider = :provider")
    Optional<UserIdentity> findByUserIdAndProvider(UUID userId, IdentityProvider provider);

    @Modifying(flushAutomatically = true)
    @Query("delete from UserIdentity i where i.user.id = :userId")
    int deleteAllByUserId(UUID userId);
}
