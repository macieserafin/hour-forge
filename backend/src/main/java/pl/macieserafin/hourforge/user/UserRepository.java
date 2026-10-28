package pl.macieserafin.hourforge.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, UUID> {
    default Optional<User> findByEmailIgnoreCase(String email) {
        return findByNormalizedEmail(EmailAddress.normalize(email));
    }

    // Match uq_users_email_lower exactly. Spring Data's derived IgnoreCase query uses upper(),
    // which treats distinct mailboxes containing dotless i or long s as the same account.
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByNormalizedEmail(String email);

    /** Accounts nobody confirmed in time: never signed in, so they own no teams, entries or invitations. */
    @Query("select u.id from User u where u.emailVerifiedAt is null and u.deletedAt is null and u.createdAt < :before"
            + " and not exists (select 1 from TeamMember m where m.user = u)"
            + " and not exists (select 1 from TimeEntry e where e.user = u)"
            + " and not exists (select 1 from TeamInvite i where i.createdBy = u)")
    List<UUID> findUnconfirmedIdsCreatedBefore(Instant before);

    /** Moves the last active day forward (an older or equal day leaves it as it is) and cancels inactivity warnings. */
    @Transactional
    @Modifying
    @Query("update User u set u.lastActiveAt = :day, u.inactivityWarnedDays = null, u.inactivityWarnedAt = null"
            + " where u.id = :id and (u.lastActiveAt is null or u.lastActiveAt < :day)")
    int markActive(UUID id, Instant day);

    // Serialize a user's timer operations without blocking FK key-share locks on this user.
    @Query(value = "select * from users where id = :id for no key update", nativeQuery = true)
    Optional<User> findLockedForTimeEntryById(UUID id);
}
