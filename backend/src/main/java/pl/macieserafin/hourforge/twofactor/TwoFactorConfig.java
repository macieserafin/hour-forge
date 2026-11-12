package pl.macieserafin.hourforge.twofactor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TwoFactorKeys.class)
class TwoFactorConfig {
}
