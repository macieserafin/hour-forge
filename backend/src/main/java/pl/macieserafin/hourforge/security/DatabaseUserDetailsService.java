package pl.macieserafin.hourforge.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserCredentialRepository credentials;
    /**
     * Stands in for the missing password of an account that signs in through a linked provider. It is a real hash of
     * a random secret nobody knows, made at start-up, so a password sign-in on such an account fails like a wrong
     * password, after the same hashing work, from the very first attempt.
     */
    private final String unusablePasswordHash;

    public DatabaseUserDetailsService(UserCredentialRepository credentials, PasswordEncoder encoder) {
        this.credentials = credentials;
        var secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        this.unusablePasswordHash = encoder.encode(Base64.getEncoder().encodeToString(secret));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var credential = credentials.findByUserEmailIgnoreCase(email.strip())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        var user = credential.getUser();
        var passwordHash = credential.hasPassword() ? credential.getPasswordHash() : unusablePasswordHash;
        return new AppUserPrincipal(user.getId(), user.getEmail(), passwordHash, credential.getSessionVersion(), user.isEmailVerified(),
                user.isBlocked());
    }
}
