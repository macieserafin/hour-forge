package pl.macieserafin.hourforge.operator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Who runs this instance: only the accounts whose (confirmed) addresses are listed in {@code OPERATOR_EMAILS}. There
 * is no way to become an operator from inside the application. The address is read from the database on every check,
 * so a changed address or a removed listing takes effect at once.
 */
@Component
public class OperatorAccess {
    private final Set<String> emails;
    private final UserRepository users;

    public OperatorAccess(@Value("${app.operator.emails:}") String emails, UserRepository users) {
        this.emails = Arrays.stream(emails.split(","))
                .map(email -> email.strip().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.users = users;
    }

    @Transactional(readOnly = true)
    public boolean isOperator(UUID userId) {
        return users.findById(userId).map(this::isOperator).orElse(false);
    }

    public boolean isOperator(User user) {
        return user.isEmailVerified() && user.getDeletedAt() == null && !user.isBlocked()
                && emails.contains(user.getEmail().toLowerCase(Locale.ROOT));
    }

    public void require(UUID userId) {
        if (!isOperator(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
