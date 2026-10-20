package pl.macieserafin.hourforge.jobs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JobsProperties.class)
public class JobsConfig {
    /** Scheduling is switched on only when the jobs are enabled, so tests and tools start quietly. */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")
    static class Scheduling {
    }
}
