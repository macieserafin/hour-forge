package pl.macieserafin.hourforge.logging;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The security event log: who did what to which account, team or other object, from which address. One line per event
 * on the {@code security} logger, as fields in the JSON log. Only identifiers and fixed values are written, never
 * e-mail addresses, names, tokens or passwords. An event inside a transaction is written only once it has committed,
 * so the log never shows a change that was rolled back.
 */
public final class SecurityEvents {
    public static final String LOGGER = "security";
    private static final Logger LOG = LoggerFactory.getLogger(LOGGER);

    private SecurityEvents() {
    }

    /**
     * @param event   what happened, e.g. {@code team.member-removed}
     * @param actor   who did it; {@code null} when unknown (a failed sign-in) or done by the system
     * @param details pairs of name and value (identifiers, roles, methods), e.g. {@code "team", teamId}
     */
    public static void record(String event, UUID actor, Object... details) {
        if (details.length % 2 != 0) throw new IllegalArgumentException("details come in name and value pairs");
        var fields = new LinkedHashMap<String, Object>();
        fields.put("event", event);
        if (actor != null) fields.put("actor", actor);
        for (int i = 0; i < details.length; i += 2) {
            if (details[i + 1] != null) fields.put((String) details[i], details[i + 1]);
        }
        var ip = clientAddress();
        if (ip != null) fields.put("ip", ip);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write(fields);
                }
            });
        } else {
            write(fields);
        }
    }

    private static void write(Map<String, Object> fields) {
        var builder = LOG.atInfo();
        var message = new StringBuilder("security event");
        fields.forEach((name, value) -> {
            builder.addKeyValue(name, value);
            message.append(' ').append(name).append('=').append(value);
        });
        builder.log(message.toString());
    }

    private static String clientAddress() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest().getRemoteAddr() : null;
    }
}
