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
import io.vanillabp.integration.adapter.migration.processservice.InMemoryWorkflowAdapterCache;
import io.vanillabp.integration.adapter.migration.processservice.WorkflowAdapterCacheStatistics;
import io.vanillabp.integration.config.VanillaBpConfigurationProperties;
import io.vanillabp.integration.processservice.SpringBootMigrationAdapterAutoConfiguration;
import io.vanillabp.integration.spi.WorkflowAdapterCache;

/**
 * Contributes the election cache the nodes of one application share. The dependency is
 * the whole wiring: this configuration builds the bean VanillaBP looks for and the
 * platform's in-memory default steps back, because the platform declares it
 * {@link ConditionalOnMissingBean} and this configuration is applied before it.
 * <p>
 * An application which brings a cache of its own still wins over both, which is what
 * {@link ConditionalOnMissingBean} on the bean below is for: an application whose
 * infrastructure is neither Hazelcast nor anything this repository supports writes its
 * own implementation, and that stays a first-class case.
 * <p>
 * The lifetimes of a hint are NOT read here. They are the platform's
 * (<code>vanillabp.workflow-adapter-cache.time-to-live</code> and
 * <code>.ended-time-to-live</code>), so moving from the in-memory default to this cache
 * changes where the hints live and nothing about how long they live.
 */
@AutoConfiguration(before = SpringBootMigrationAdapterAutoConfiguration.class)
@ConditionalOnProperty(
    name = "vanillabp.workflow-adapter-cache.hazelcast.enabled",
    matchIfMissing = true)
@EnableConfigurationProperties({
    SpringHazelcastElectionCacheProperties.class, VanillaBpConfigurationProperties.class
})
public class HazelcastElectionCacheAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(HazelcastElectionCacheAutoConfiguration.class);

  /**
   * The election cache of the cluster, or the in-memory cache of this node where the
   * Hazelcast member could not be started. A cache which is not shared is a cost and
   * not a defect - it probes the adapters again where a hint would have answered - so
   * an application whose cluster does not come up boots and works.
   *
   * @param properties This cache's own section, validated here rather than on first use
   * @param platformProperties The platform's section, for the two lifetimes of a hint
   * @param environment The Spring environment, for the application's own name, which a
   *          cluster name is derived from
   * @param applicationsInstance The Hazelcast the application provides, if it provides
   *          one: a second member in the same JVM would be waste and a second cluster
   *          to reason about
   * @param statistics The platform's cache statistics, read only where the in-memory
   *          fallback is built
   * @return The cache VanillaBP consults before it probes the adapters
   */
  @Bean
  @ConditionalOnMissingBean(WorkflowAdapterCache.class)
  public WorkflowAdapterCache vanillaBpHazelcastElectionCache(
      final SpringHazelcastElectionCacheProperties properties,
      final VanillaBpConfigurationProperties platformProperties,
      final Environment environment,
      final ObjectProvider<HazelcastInstance> applicationsInstance,
      final ObjectProvider<WorkflowAdapterCacheStatistics> statistics) {

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
      return new InMemoryWorkflowAdapterCache(lifetimes, statistics.getIfAvailable());
    }

  }

}
