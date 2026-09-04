package io.vanillabp.electioncache.hazelcast.quarkus.runtime;

import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.HazelcastInstance;

import io.smallrye.config.SmallRyeConfig;
import io.vanillabp.electioncache.hazelcast.ElectionCacheMember;
import io.vanillabp.electioncache.hazelcast.ElectionCacheMessages;
import io.vanillabp.integration.adapter.migration.processservice.InMemoryWorkflowAdapterCache;
import io.vanillabp.integration.adapter.migration.processservice.WorkflowAdapterCacheStatistics;
import io.vanillabp.integration.runtime.config.QuarkusMigrationAdapterProperties;
import io.vanillabp.integration.runtime.config.QuarkusMigrationAdapterPropertiesMapper;
import io.vanillabp.integration.spi.WorkflowAdapterCache;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Contributes the election cache the nodes of one application share. The dependency is
 * the whole wiring: this bean is not a {@code DefaultBean} while the platform's
 * in-memory cache is, so the platform's steps back on its own.
 * <p>
 * The lifetimes of a hint are NOT this repository's. They are read from the platform's
 * own section (<code>vanillabp.workflow-adapter-cache.time-to-live</code> and
 * <code>.ended-time-to-live</code>), so moving from the in-memory default to this cache
 * changes where the hints live and nothing about how long they live.
 * <p>
 * The configuration is READ rather than injected, for the reason the platform's own
 * producer states: injecting a config mapping of the <code>vanillabp</code> tree makes
 * it a static-init mapping, and the tree would then be validated before the extensions
 * registered their run-time overlays.
 */
@ApplicationScoped
public class HazelcastElectionCacheProducer {

  private static final Logger log = LoggerFactory.getLogger(HazelcastElectionCacheProducer.class);

  /**
   * The member started here, kept so that it is shut down with the application. Null
   * where the application provided no Hazelcast and none could be started - the cache
   * is then the platform's in-memory one and there is nothing to shut down.
   */
  private ElectionCacheMember member;

  /**
   * @param statistics The platform's cache statistics, read only where the in-memory
   *          fallback is built
   * @param applicationsInstance The Hazelcast the application provides, if it provides
   *          one: a second member in the same JVM would be waste and a second cluster
   *          to reason about
   * @return The cache VanillaBP consults before it probes the adapters
   */
  @Produces
  @Singleton
  public WorkflowAdapterCache hazelcastElectionCache(
      final WorkflowAdapterCacheStatistics statistics,
      final Instance<HazelcastInstance> applicationsInstance) {

    final var properties = ConfigProvider
        .getConfig()
        .unwrap(SmallRyeConfig.class)
        .getConfigMapping(VanillaBpElectionCacheProperties.class)
        .toCore();

    final var lifetimes = QuarkusMigrationAdapterPropertiesMapper.INSTANCE
        .toCore(
            ConfigProvider
                .getConfig()
                .unwrap(SmallRyeConfig.class)
                .getConfigMapping(QuarkusMigrationAdapterProperties.class)
                .workflowAdapterCache());

    if (!properties.isEnabled()) {
      // the same cache the platform would have produced: a producer cannot decline to
      // produce, so switching this off means building the default here
      return new InMemoryWorkflowAdapterCache(lifetimes, statistics);
    }

    properties.validate();

    final var applicationName = ConfigProvider
        .getConfig()
        .getOptionalValue("quarkus.application.name", String.class)
        .orElse(null);

    properties
        .warnings(applicationName)
        .forEach(log::warn);

    try {
      member = ElectionCacheMember
          .start(
              properties,
              applicationName,
              lifetimes.getTimeToLive(),
              lifetimes.getEndedTimeToLive(),
              applicationsInstance.isResolvable()
                  ? applicationsInstance.get()
                  : null);
      return member.cache();
    } catch (final RuntimeException e) {
      log.warn(ElectionCacheMessages.startFailed(properties), e);
      return new InMemoryWorkflowAdapterCache(lifetimes, statistics);
    }

  }

  /**
   * Shuts the member down with the application, and only where it is ours: an instance
   * the application provided keeps running, because shutting somebody else's Hazelcast
   * down would take their caches with it.
   */
  @PreDestroy
  public void stopTheMember() {

    if (member == null) {
      return;
    }
    member.close();
    member = null;

  }

}
