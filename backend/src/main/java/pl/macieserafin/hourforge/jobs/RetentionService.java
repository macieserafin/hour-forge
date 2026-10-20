package pl.macieserafin.hourforge.jobs;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.auth.EmailTokenRepository;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.invitation.TeamInviteRepository;
import pl.macieserafin.hourforge.jira.OAuthStateRepository;
import pl.macieserafin.hourforge.user.UserRepository;

/** Removes data that has outlived its purpose: unconfirmed accounts, spent one-time tokens and stale invitations. */
@Service
public class RetentionService {
    public record Purged(int accounts, int tokens, int oauthStates, int invitations) {
    }

    private final JobsProperties properties;
    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final EmailTokenRepository tokens;
    private final OAuthStateRepository oauthStates;
    private final TeamInviteRepository invitations;

    public RetentionService(JobsProperties properties, UserRepository users, UserCredentialRepository credentials,
                            EmailTokenRepository tokens, OAuthStateRepository oauthStates, TeamInviteRepository invitations) {
        this.properties = properties;
        this.users = users;
        this.credentials = credentials;
        this.tokens = tokens;
        this.oauthStates = oauthStates;
        this.invitations = invitations;
    }

    @Transactional
    public Purged purge(Instant now) {
        int accounts = 0;
        for (var id : users.findUnconfirmedIdsCreatedBefore(now.minus(properties.unconfirmedAccountAge()))) {
            // The confirmation tokens of the account go with it, before the tokens are swept by age.
            tokens.deleteByUserId(id);
            credentials.deleteByUserId(id);
            users.deleteById(id);
            accounts++;
        }
        users.flush();
        int spent = tokens.deleteSpent(now.minus(properties.spentTokenAge()));
        int states = oauthStates.deleteExpired(now.minus(properties.spentTokenAge()));
        int stale = invitations.deleteStale(now.minus(properties.staleInvitationAge()));
        return new Purged(accounts, spent, states, stale);
    }
}
