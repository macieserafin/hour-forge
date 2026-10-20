package pl.macieserafin.hourforge.jira;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JiraProperties.class)
public class JiraConfig {
    /** Only exists when the integration is on; an incomplete configuration then stops the application at startup. */
    @Bean
    @ConditionalOnProperty(name = "app.jira.enabled", havingValue = "true")
    TokenCipher tokenCipher(JiraProperties properties) {
        properties.requireComplete();
        return new TokenCipher(properties.encryptionKeyVersion(), properties.encryptionKey(), properties.previousEncryptionKeys());
    }
}
