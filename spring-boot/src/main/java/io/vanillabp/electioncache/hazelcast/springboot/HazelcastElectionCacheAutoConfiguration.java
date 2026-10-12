package io.vanillabp.electioncache.hazelcast.springboot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import com.hazelcast.core.HazelcastInstance;

import io.vanillabp.electioncache.hazelcast.ElectionCacheMember;
import io.vanillabp.electioncache.hazelcast.ElectionCacheMessages;
import io.vanillabp.electioncache.hazelcast.HazelcastElectionCacheProperties;
import io.vanillabp.integration.adapter.migration.processservice.InMemoryWorkflowAdapterCache;
import io.vanillabp.integration.config.VanillaBpConfigurationProperties;
import io.vanillabp.integration.processservice.SpringBootMigrationAdapterAutoConfiguration;
import io.vanillabp.integration.spi.WorkflowAdapterCache;

/**
 * Provides the election cache which the nodes of one application share. Adding the dependency is
 * all the wiring there is. This configuration builds the bean VanillaBP looks for, and the
 * in-memory default of the platform steps back. The platform declares its default
 * {@link ConditionalOnMissingBean}, and this configuration is applied before it.
 * <p>
 * An application which brings a cache of its own still wins over both. That is what
 * {@link ConditionalOnMissingBean} on the bean below is for. An application may use neither
 * Hazelcast nor anything else this repository supports. It writes its own implementation, and that
 * case stays fully supported.
 * <p>
 * The lifetimes of a hint are NOT read here. They belong to the platform
 * (<code>vanillabp.workflow-adapter-cache.time-to-live</code> and
 * <code>.ended-time-to-live</code>). So moving from the in-memory default to this cache changes
 * where the hints live, but not how long they live.
 */
@AutoConfiguration(before = SpringBootMigrationAdapterAutoConfiguration.class)
@ConditionalOnProperty(name = HazelcastElectionCacheProperties.ENABLED_PROPERTY, matchIfMissing = true)
@EnableConfigurationProperties({
    SpringHazelcastElectionCacheProperties.class, VanillaBpConfigurationProperties.class
})
public class HazelcastElectionCacheAutoConfiguration {

  /**
   * Spring Boot creates this class once, when it applies the auto-configuration. It holds no state
   * of its own. The member and the cache it produces below are beans, and they live as long as
   * the application context.
   */
  public HazelcastElectionCacheAutoConfiguration() {

  }

  private static final Logger log = LoggerFactory.getLogger(HazelcastElectionCacheAutoConfiguration.class);

  /**
   * The election cache of the cluster, or the in-memory cache of this node if the Hazelcast member
   * could not be started. A cache which is not shared costs something, but it is not a defect. It
   * asks the adapters again where a hint would have answered. So an application whose cluster does
   * not come up still starts and works.
   *
   * @param properties The section of this cache. It is checked here, not on first use
   * @param platformProperties The section of the platform, for the two lifetimes of a hint
   * @param environment The Spring environment, for the name of the application. A cluster name
   *          is made from it
   * @param applicationsInstance The Hazelcast the application provides, if it provides one. A
   *          second member in the same JVM would waste resources, and it would be a second
   *          cluster to think about
   * @return The cache VanillaBP consults before it probes the adapters
   */
  @Bean
  @ConditionalOnMissingBean(WorkflowAdapterCache.class)
  public WorkflowAdapterCache vanillaBpHazelcastElectionCache(
      final SpringHazelcastElectionCacheProperties properties,
      final VanillaBpConfigurationProperties platformProperties,
      final Environment environment,
      final ObjectProvider<HazelcastInstance> applicationsInstance) {

    properties.validate();

    final var applicationName = environment.getProperty("spring.application.name");

    properties
        .warnings(applicationName)
        .forEach(log::warn);

    final var lifetimes = platformProperties.getWorkflowAdapterCache();

    try {
      return new ManagedElectionCache(
          ElectionCacheMember
              .start(
                  properties,
                  applicationName,
                  lifetimes.getTimeToLive(),
                  lifetimes.getEndedTimeToLive(),
                  applicationsInstance.getIfAvailable()));
    } catch (final RuntimeException e) {
      log.warn(ElectionCacheMessages.startFailed(properties), e);
      // the default of the platform, which the platform would have built itself. Its
      // numbers come with it and are published under its name
      return new InMemoryWorkflowAdapterCache(lifetimes);
    }

  }

}
