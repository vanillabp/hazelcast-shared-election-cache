package io.vanillabp.electioncache.hazelcast.quarkus.runtime;

import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.HazelcastInstance;

import io.smallrye.config.SmallRyeConfig;
import io.vanillabp.electioncache.hazelcast.ElectionCacheMember;
import io.vanillabp.electioncache.hazelcast.ElectionCacheMessages;
import io.vanillabp.integration.adapter.migration.processservice.InMemoryWorkflowAdapterCache;
import io.vanillabp.integration.runtime.config.QuarkusMigrationAdapterProperties;
import io.vanillabp.integration.runtime.config.QuarkusMigrationAdapterPropertiesMapper;
import io.vanillabp.integration.spi.WorkflowAdapterCache;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Provides the election cache which the nodes of one application share. Adding the dependency is
 * all the wiring there is. This bean is not a {@code DefaultBean}, but the in-memory cache of the
 * platform is. So the cache of the platform steps back on its own.
 * <p>
 * The lifetimes of a hint do NOT belong to this repository. They are read from the section of the
 * platform (<code>vanillabp.workflow-adapter-cache.time-to-live</code> and
 * <code>.ended-time-to-live</code>). So moving from the in-memory default to this cache changes
 * where the hints live, but not how long they live.
 * <p>
 * The configuration is READ, not injected, for the reason the producer of the platform gives.
 * Injecting a config mapping of the <code>vanillabp</code> tree makes it a static-init mapping.
 * The tree would then be checked before the extensions registered their run-time overlays.
 */
@ApplicationScoped
public class HazelcastElectionCacheProducer {

  /**
   * Quarkus creates the bean. The bean keeps the member it started, so that
   * {@link #stopTheMember()} can stop it with the application.
   */
  public HazelcastElectionCacheProducer() {

  }

  private static final Logger log = LoggerFactory.getLogger(HazelcastElectionCacheProducer.class);

  /**
   * The member started here, kept so that it shuts down with the application. It is null if the
   * application provided no Hazelcast and none could be started. The cache is then the in-memory
   * one of the platform, and there is nothing to shut down.
   */
  private ElectionCacheMember member;

  /**
   * The cache the nodes share, or the in-memory cache of the platform if this node could not join a
   * cluster. A producer cannot refuse to produce. So the fallback is built here, and not left to
   * the platform.
   *
   * @param applicationsInstance The Hazelcast the application provides, if it provides one. A
   *          second member in the same JVM would waste resources, and it would be a second
   *          cluster to think about
   * @return The cache VanillaBP consults before it probes the adapters
   */
  @Produces
  @Singleton
  public WorkflowAdapterCache hazelcastElectionCache(
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
      // the same cache the platform would have produced. A producer cannot refuse to
      // produce, so switching this off means building the default here
      return new InMemoryWorkflowAdapterCache(lifetimes);
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
      return new InMemoryWorkflowAdapterCache(lifetimes);
    }

  }

  /**
   * Shuts the member down with the application, but only if it is ours. An instance which the
   * application provided keeps running. Shutting down somebody else's Hazelcast would take their
   * caches with it.
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
