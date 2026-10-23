package pl.macieserafin.hourforge.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserCredentialRepository credentials;

    public DatabaseUserDetailsService(UserCredentialRepository credentials) {
        this.credentials = credentials;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var credential = credentials.findByUserEmailIgnoreCase(email.strip())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        var user = credential.getUser();
        return new AppUserPrincipal(user.getId(), user.getEmail(), credential.getPasswordHash(), credential.getSessionVersion(), user.isEmailVerified(),
                user.isBlocked());
    }
}
