package pl.macieserafin.hourforge.invitation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TeamInviteRepository extends JpaRepository<TeamInvite, UUID> {
}
