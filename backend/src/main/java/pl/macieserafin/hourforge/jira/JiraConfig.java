package pl.macieserafin.hourforge.jira;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JiraProperties.class)
public class JiraConfig {
    private static final Logger log = LoggerFactory.getLogger(JiraConfig.class);

    /** Only exists when the integration is on; an incomplete configuration then stops the application at startup. */
    @Bean
    @ConditionalOnProperty(name = "app.jira.enabled", havingValue = "true")
    TokenCipher tokenCipher(JiraProperties properties) {
        properties.requireComplete();
        if (!"https://auth.atlassian.com".equals(properties.authBaseUrl()) || !"https://api.atlassian.com".equals(properties.apiBaseUrl())) {
            // Only test setups point the integration at a stand-in; tokens and the client secret go to these addresses.
            log.warn("Jira integration does not use the Atlassian hosts (auth: {}, api: {}); this is meant for tests only",
                    properties.authBaseUrl(), properties.apiBaseUrl());
        }
        return new TokenCipher(properties.encryptionKeyVersion(), properties.encryptionKey(), properties.previousEncryptionKeys());
    }
}
