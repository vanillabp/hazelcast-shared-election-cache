package io.vanillabp.electioncache.hazelcast;

import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.hazelcast.core.HazelcastInstance;

import io.vanillabp.electioncache.hazelcast.HazelcastElectionCacheProperties.Discovery;
import io.vanillabp.integration.spi.WorkflowAdapterCache;
import io.vanillabp.integration.test.utils.FreePortUtil;

/**
 * Two members in one JVM, which is the cheapest cluster there is and proves the one
 * thing that matters: what one node writes, another node reads.
 * <p>
 * They find each other by a list of addresses rather than by multicast, because a
 * container network filters multicast and a test which depends on it is a test which
 * fails on somebody else's machine. Each member is started through the same code an
 * application starts it with, so the configuration this repository builds is the
 * configuration under test.
 */
final class TestCluster implements AutoCloseable {

  private final ElectionCacheMember first;

  private final ElectionCacheMember second;

  private TestCluster(
      final ElectionCacheMember first,
      final ElectionCacheMember second) {

    this.first = first;
    this.second = second;

  }

  /**
   * Starts two members which have found each other.
   *
   * @param clusterName A name of this test's own, so two test classes running in one
   *          JVM never end up in one cluster
   * @param timeToLive The lifetime of the hint of a living workflow
   * @param endedTimeToLive The lifetime of the hint of a workflow which ended
   * @return The cluster
   */
  static TestCluster ofTwoNodes(
      final String clusterName,
      final Duration timeToLive,
      final Duration endedTimeToLive) {

    final var seeds = seedRange();

    final var first = ElectionCacheMember
        .start(properties(clusterName, seeds), null, timeToLive, endedTimeToLive, null);
    final var second = ElectionCacheMember
        .start(properties(clusterName, seeds), null, timeToLive, endedTimeToLive, null);

    await(
        () -> (first.instance().getCluster().getMembers().size() == 2) && (second.instance().getCluster().getMembers()
            .size() == 2),
        "the two members did not find each other");

    return new TestCluster(first, second);

  }

  /**
   * A range of addresses starting at a port which was free a moment ago. The members of
   * a test take the first free port of that range and look for each other along all of
   * it, which is what keeps a port left in TIME_WAIT by the previous test class from
   * failing the next one. Which member ends up on which port does not matter: a seed
   * list is a list of addresses to try.
   *
   * @return The addresses
   */
  static List<String> seedRange() {

    final var basePort = FreePortUtil.getFreePort();

    return List
        .of(
            "127.0.0.1:"
                + basePort,
            "127.0.0.1:"
                + (basePort + 1),
            "127.0.0.1:"
                + (basePort + 2),
            "127.0.0.1:"
                + (basePort + 3));

  }

  /**
   * The configuration of one member: a cluster of this test's own and the seed range to
   * look in. What separates two test classes running in one JVM is the cluster name,
   * not the port.
   *
   * @param clusterName The cluster of this test
   * @param seeds The addresses to try
   * @return The configuration
   */
  static HazelcastElectionCacheProperties properties(
      final String clusterName,
      final List<String> seeds) {

    final var properties = new HazelcastElectionCacheProperties();
    properties.setClusterName(clusterName);
    properties.setDiscovery(Discovery.MEMBERS);
    properties.setMembers(seeds);
    properties.setPort(Integer.parseInt(seeds.get(0).split(":")[1]));
    properties.setPortAutoIncrement(true);
    // a test does not need to be reminded about being alone every five minutes
    properties.setAloneReminderInterval(Duration.ofHours(1));
    properties.validate();
    return properties;

  }

  /**
   * The cache of the node which writes.
   *
   * @return The cache
   */
  WorkflowAdapterCache nodeA() {

    return first.cache();

  }

  /**
   * The cache of the node which reads - a different member of the same cluster, which
   * is the whole point.
   *
   * @return The cache
   */
  WorkflowAdapterCache nodeB() {

    return second.cache();

  }

  /**
   * The Hazelcast of the second node, for a test which takes a member away.
   *
   * @return The instance
   */
  HazelcastInstance instanceOfNodeB() {

    return second.instance();

  }

  /**
   * Waits for something the cluster does on its own thread, with an end: a condition
   * which never comes true is a failed test and not a hanging build.
   *
   * @param condition What is waited for
   * @param whatWasExpected What the failure says
   */
  static void await(
      final BooleanSupplier condition,
      final String whatWasExpected) {

    final var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      try {
        Thread.sleep(50);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted while waiting for "
            + whatWasExpected, e);
      }
    }
    throw new AssertionError("Waited 30 seconds in vain: "
        + whatWasExpected);

  }

  @Override
  public void close() {

    try {
      first.close();
    } finally {
      second.close();
    }

  }

}
