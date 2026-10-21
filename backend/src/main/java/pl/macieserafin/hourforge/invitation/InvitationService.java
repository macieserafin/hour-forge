package pl.macieserafin.hourforge.invitation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMember;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamResponse;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.UserRepository;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class InvitationService {
    public static final Duration MAX_VALIDITY = Duration.ofDays(30);
    public static final Duration DEFAULT_VALIDITY = Duration.ofDays(7);
    private final TeamInviteRepository invites;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final UserRepository users;
    private final String frontendUrl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public InvitationService(TeamInviteRepository invites, TeamRepository teams, TeamMemberRepository members,
                             UserRepository users, @Value("${app.frontend-url}") String frontendUrl, Clock clock) {
        this.clock = clock;
        this.invites = invites;
        this.teams = teams;
        this.members = members;
        this.users = users;
        URI uri = URI.create(frontendUrl);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("FRONTEND_URL must be an absolute HTTP(S) URL without query, fragment or credentials");
        }
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public InviteResponse create(UUID currentUserId, UUID teamId, CreateInviteRequest request) {
        var creator = users.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var team = teams.findLockedById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var membership = members.findByTeamIdAndUserId(teamId, currentUserId)
                .filter(member -> member.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!membership.getRole().canManage()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (team.isArchived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived teams cannot invite new members");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant expiry = request.expiresAt() == null ? now.plus(DEFAULT_VALIDITY) : request.expiresAt();
        int maxUses = request.maxUses() == null ? 1 : request.maxUses();
        if (!expiry.isAfter(now) || expiry.isAfter(now.plus(MAX_VALIDITY)) || maxUses < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiration must be within 30 days and use limit positive");
        }

        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        var invite = invites.saveAndFlush(new TeamInvite(team, creator, hash(token), maxUses, expiry, now));
        String url = UriComponentsBuilder.fromUriString(frontendUrl).pathSegment("invite", token).build().toUriString();
        return new InviteResponse(invite.getId(), teamId, url, token, invite.getExpiresAt(), invite.getMaxUses());
    }

    @Transactional
    public TeamResponse accept(UUID currentUserId, String token) {
        var user = users.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found");
        }
        String tokenHash = hash(token);
        UUID teamId = invites.findTeamIdByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));
        // Serializing by team also prevents duplicate memberships through DIFFERENT invite tokens.
        var team = teams.findLockedById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var invite = invites.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));
        Instant now = clock.instant();
        if (invite.getRevokedAt() != null || !invite.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invitation is no longer valid");
        }
        var existing = members.findByTeamIdAndUserId(teamId, currentUserId);
        if (existing.isPresent() && existing.get().getLeftAt() == null) {
            return response(team, existing.get().getRole());
        }
        if (team.isArchived()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invitation is no longer valid");
        }
        if (existing.isPresent() && requiresNewInvitation(existing.get(), invite)) {
            throw new ResponseStatusException(HttpStatus.GONE, "A new invitation is required after removal");
        }
        if ((invite.getMaxUses() != null && invite.getUsedCount() >= invite.getMaxUses())
                || invite.getUsedCount() == Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation use limit reached");
        }

        var membership = existing.orElseGet(() -> new TeamMember(team, user, TeamRole.MEMBER, now));
        membership.setRole(TeamRole.MEMBER);
        membership.setJoinedAt(now);
        membership.setLeftAt(null);
        membership.setRemovedAt(null);
        members.saveAndFlush(membership);
        invite.setUsedCount(invite.getUsedCount() + 1);
        invites.saveAndFlush(invite);
        return response(team, TeamRole.MEMBER);
    }

    @Transactional(readOnly = true)
    public InvitePreviewResponse preview(UUID currentUserId, String token) {
        if (!users.existsById(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found");
        }
        var invite = invites.findByTokenHash(hash(token))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));
        var team = invite.getTeam();
        var existing = members.findByTeamIdAndUserId(team.getId(), currentUserId);
        boolean member = existing.filter(membership -> membership.getLeftAt() == null).isPresent();
        InvitePreviewStatus status;
        if (member) status = InvitePreviewStatus.ALREADY_MEMBER;
        else if (invite.getRevokedAt() != null) status = InvitePreviewStatus.REVOKED;
        else if (!invite.getExpiresAt().isAfter(clock.instant())) status = InvitePreviewStatus.EXPIRED;
        else if (team.isArchived()) status = InvitePreviewStatus.TEAM_ARCHIVED;
        else if (existing.isPresent() && requiresNewInvitation(existing.get(), invite)) status = InvitePreviewStatus.NEW_INVITATION_REQUIRED;
        else if (invite.getUsedCount() == Integer.MAX_VALUE || (invite.getMaxUses() != null && invite.getUsedCount() >= invite.getMaxUses())) status = InvitePreviewStatus.EXHAUSTED;
        else status = InvitePreviewStatus.ACTIVE;
        // Only what the invitee needs to decide: no members, projects or owner identity.
        return new InvitePreviewResponse(team.getId(), team.getName(), status, invite.getExpiresAt());
    }

    private TeamResponse response(Team team, TeamRole role) {
        return TeamResponse.of(team, role);
    }

    private boolean requiresNewInvitation(TeamMember member, TeamInvite invite) {
        return member.getRemovedAt() != null && !invite.getCreatedAt().isAfter(member.getRemovedAt());
    }

    @Transactional(readOnly = true)
    public InviteSummaryResponse.Page list(UUID actorId, UUID teamId, int page, int size) {
        requireOwner(actorId, teamId);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        var rows = invites.findByTeamId(teamId, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
        Instant now = clock.instant();
        var content = rows.getContent().stream().map(invite -> new InviteSummaryResponse(invite.getId(), invite.getCreatedAt(),
                invite.getExpiresAt(), invite.getMaxUses(), invite.getUsedCount(), invite.getRevokedAt(), status(invite, now))).toList();
        return new InviteSummaryResponse.Page(content, page, size, rows.getTotalElements(), rows.getTotalPages());
    }

    private InviteSummaryResponse.Status status(TeamInvite invite, Instant now) {
        if (invite.getRevokedAt() != null) return InviteSummaryResponse.Status.REVOKED;
        if (!invite.getExpiresAt().isAfter(now)) return InviteSummaryResponse.Status.EXPIRED;
        if (invite.getUsedCount() == Integer.MAX_VALUE || (invite.getMaxUses() != null && invite.getUsedCount() >= invite.getMaxUses())) {
            return InviteSummaryResponse.Status.EXHAUSTED;
        }
        return InviteSummaryResponse.Status.ACTIVE;
    }

    @Transactional
    public void revoke(UUID actorId, UUID teamId, UUID inviteId) {
        if (!users.existsById(actorId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        teams.findLockedById(teamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var team = requireOwner(actorId, teamId);
        if (team.isArchived()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived teams cannot be changed");
        var invite = invites.findByIdAndTeamId(inviteId, teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (invite.getRevokedAt() == null) {
            invite.setRevokedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
            invites.saveAndFlush(invite);
        }
    }

    private Team requireOwner(UUID actorId, UUID teamId) {
        if (!users.existsById(actorId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var member = members.findByTeamIdAndUserId(teamId, actorId).filter(m -> m.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!member.getRole().canManage()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return member.getTeam();
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
