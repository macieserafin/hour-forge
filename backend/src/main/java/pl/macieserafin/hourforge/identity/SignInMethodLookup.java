package pl.macieserafin.hourforge.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;

import java.util.Optional;
import java.util.UUID;

/** How a person can sign in: with a password, through a linked Google account, or both. */
@Service
public class SignInMethodLookup {
    private final UserCredentialRepository credentials;
    private final UserIdentityRepository identities;

    public SignInMethodLookup(UserCredentialRepository credentials, UserIdentityRepository identities) {
        this.credentials = credentials;
        this.identities = identities;
    }

    @Transactional(readOnly = true)
    public boolean hasPassword(UUID userId) {
        return credentials.findById(userId).filter(credential -> credential.hasPassword()).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<UserIdentity> google(UUID userId) {
        return identities.findByUserIdAndProvider(userId, IdentityProvider.GOOGLE);
    }
}
