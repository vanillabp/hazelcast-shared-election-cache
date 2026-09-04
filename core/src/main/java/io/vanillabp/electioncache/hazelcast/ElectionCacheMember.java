package io.vanillabp.electioncache.hazelcast;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import io.vanillabp.integration.spi.WorkflowAdapterCache;

/**
 * The election cache and the Hazelcast it lives in, as one thing with one lifetime.
 * Both platform integrations build this and hand its {@link #cache()} to VanillaBP;
 * everything about the cluster is decided here, so the two integrations stay what they
 * are meant to be, namely configuration binding and bean creation.
 * <p>
 * Started here means started here: a member of our own is configured from
 * {@link HazelcastElectionCacheProperties} alone and shut down with the application.
 * Where the application provides an instance itself, this class moves in rather than
 * starting a second member in the same JVM, and leaves that instance alone on the way
 * out - shutting somebody else's Hazelcast down would take their caches with it.
 */
public class ElectionCacheMember implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(ElectionCacheMember.class);

  private final HazelcastInstance instance;

  private final boolean instanceIsOurs;

  private final ClusterVisibility visibility;

  private final HazelcastElectionCache cache;

  private final String clusterName;

  /**
   * Starts the member and the cache in it.
   *
   * @param properties What the application configured under
   *          <code>vanillabp.workflow-adapter-cache.hazelcast</code>, validated by the
   *          caller
   * @param applicationName The application's own name, which a cluster name is derived
   *          from where none is configured; may be <code>null</code>
   * @param timeToLive How long the hint of a living workflow is kept
   *          (<code>vanillabp.workflow-adapter-cache.time-to-live</code>)
   * @param endedTimeToLive How long the hint of a workflow which ended is kept
   * @param applicationsInstance The Hazelcast the application provides, or
   *          <code>null</code> where it provides none
   * @return The member, running
   * @throws RuntimeException Whatever Hazelcast throws when a member cannot be started.
   *           The caller answers that with
   *           {@link ElectionCacheMessages#startFailed(HazelcastElectionCacheProperties)}
   *           and the in-memory cache of the platform, because an application which
   *           cannot share its elections still works
   */
  public static ElectionCacheMember start(
      final HazelcastElectionCacheProperties properties,
      final String applicationName,
      final Duration timeToLive,
      final Duration endedTimeToLive,
      final HazelcastInstance applicationsInstance) {

    final var useTheApplications = (applicationsInstance != null) && properties.isUseExistingInstance();

    if (useTheApplications) {
      final var clusterName = applicationsInstance.getConfig().getClusterName();
      log.info(ElectionCacheMessages.usingTheApplicationsInstance(properties.getMapName(), clusterName));
      return new ElectionCacheMember(
          applicationsInstance, false, clusterName, properties, timeToLive, endedTimeToLive);
    }

    final var clusterName = properties.clusterNameOrDerived(applicationName);
    final var config = ElectionCacheMemberConfig
        .of(properties, clusterName, ElectionCacheMember.class.getClassLoader());

    return new ElectionCacheMember(
        Hazelcast.newHazelcastInstance(config), true, clusterName, properties, timeToLive, endedTimeToLive);

  }

  private ElectionCacheMember(
      final HazelcastInstance instance,
      final boolean instanceIsOurs,
      final String clusterName,
      final HazelcastElectionCacheProperties properties,
      final Duration timeToLive,
      final Duration endedTimeToLive) {

    this.instance = instance;
    this.instanceIsOurs = instanceIsOurs;
    this.clusterName = clusterName;
    this.cache = new HazelcastElectionCache(
        instance, properties
            .getMapName(), timeToLive, endedTimeToLive, new ElectionCacheFailures(properties.getFailureLogInterval()));
    this.visibility = new ClusterVisibility(instance, clusterName, properties, timeToLive, endedTimeToLive);
    this.visibility.start();

  }

  /**
   * The cache to hand to VanillaBP.
   *
   * @return The cache
   */
  public WorkflowAdapterCache cache() {

    return cache;

  }

  /**
   * The Hazelcast the hints live in - the one we started or the one the application
   * provided.
   *
   * @return The instance
   */
  public HazelcastInstance instance() {

    return instance;

  }

  /**
   * The cluster the hints are shared in, as the startup line named it.
   *
   * @return The cluster name
   */
  public String clusterName() {

    return clusterName;

  }

  @Override
  public void close() {

    visibility.close();

    if (instanceIsOurs) {
      instance.shutdown();
    }

  }

}
