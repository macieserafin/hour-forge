package pl.macieserafin.hourforge.google;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * True when {@code app.google.client-id} has a value. An empty value is the default ({@code GOOGLE_CLIENT_ID} unset),
 * which {@code @ConditionalOnProperty} would still take as present.
 */
public class GoogleSignInEnabled implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var clientId = context.getEnvironment().getProperty("app.google.client-id");
        return clientId != null && !clientId.isBlank();
    }
}
