package io.vanillabp.electioncache.hazelcast;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.cluster.MembershipListener;
import com.hazelcast.core.HazelcastInstance;

/**
 * What the log says about the cluster. The log is the only place where an operator can see
 * whether the cache is shared at all.
 * <p>
 * A member which finds nobody forms a cluster of one, and everything looks fine. There is no
 * exception and no failed request, and the application behaves exactly as with the in-memory
 * default. So the size of the cluster is logged at startup. Every change of the members is logged
 * with the size after it. And a member which is alone says so again and again, for as long as it
 * stays alone. Nobody should need a debugger to tell "the cache is shared" from "the cache
 * exists".
 */
public class ClusterVisibility implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(ClusterVisibility.class);

  private final HazelcastInstance instance;

  private final String clusterName;

  private final HazelcastElectionCacheProperties properties;

  private final Duration timeToLive;

  private final Duration endedTimeToLive;

  private ScheduledExecutorService aloneReminder;

  private UUID membershipListener;

  /**
   * Collects what the log lines report. Nothing is logged and nothing is watched until
   * {@link #start()} is called. So a member which fails to start says nothing about a cluster it
   * never joined.
   *
   * @param instance The Hazelcast member whose cluster is watched
   * @param clusterName The name of the cluster. An operator searches the log for it
   * @param properties The section of this cache: the map, the discovery, and how often a member
   *          which is alone repeats its warning
   * @param timeToLive How long the hint of a running workflow is kept. It is logged at startup,
   *          because it is configured in the section of the platform, not in this one
   * @param endedTimeToLive How long the hint of a workflow which ended is kept
   */
  public ClusterVisibility(
      final HazelcastInstance instance,
      final String clusterName,
      final HazelcastElectionCacheProperties properties,
      final Duration timeToLive,
      final Duration endedTimeToLive) {

    this.instance = instance;
    this.clusterName = clusterName;
    this.properties = properties;
    this.timeToLive = timeToLive;
    this.endedTimeToLive = endedTimeToLive;

  }

  /**
   * Logs the state of the cluster and starts watching it.
   */
  public void start() {

    final var cluster = instance.getCluster();

    log
        .info(
            ElectionCacheMessages
                .sharing(
                    clusterName,
                    cluster.getLocalMember().getAddress().toString(),
                    cluster.getMembers().size(),
                    properties.getMapName(),
                    properties.getBackupCount(),
                    timeToLive,
                    endedTimeToLive,
                    ElectionCacheMessages.discoveryDescription(properties)));

    membershipListener = cluster.addMembershipListener(new Membership());

    aloneReminder = Executors
        .newSingleThreadScheduledExecutor(runnable -> {
          final var thread = new Thread(runnable, "vanillabp-election-cache-alone-check");
          thread.setDaemon(true);
          return thread;
        });
    // starts at once: a node which comes up alone should say so right after the line
    // above, not five minutes later
    aloneReminder
        .scheduleWithFixedDelay(
            this::warnWhileAlone,
            0,
            properties.getAloneReminderInterval().toMillis(),
            TimeUnit.MILLISECONDS);

  }

  /**
   * Warns while this member is the whole cluster. The warning repeats on purpose. This is the
   * one state which is wrong and still invisible.
   */
  private void warnWhileAlone() {

    try {
      if (instance.getCluster().getMembers().size() > 1) {
        return;
      }
      log.warn(ElectionCacheMessages.alone(clusterName, properties));
    } catch (final Exception e) {
      // a member which shuts down answers nothing, and a reminder is not worth a stack
      // trace on the way out
      log.debug("The VanillaBP election cache could not read its cluster's size", e);
    }

  }

  @Override
  public void close() {

    if (aloneReminder != null) {
      aloneReminder.shutdownNow();
      aloneReminder = null;
    }
    if (membershipListener != null) {
      try {
        instance.getCluster().removeMembershipListener(membershipListener);
      } catch (final Exception e) {
        log.debug("The VanillaBP election cache could not remove its membership listener", e);
      }
      membershipListener = null;
    }

  }

  /**
   * Logs every member which joins or leaves, with the size of the cluster after it.
   */
  private class Membership implements MembershipListener {

    @Override
    public void memberAdded(
        final MembershipEvent event) {

      log
          .info(
              ElectionCacheMessages
                  .memberJoined(
                      event.getMember().getAddress().toString(),
                      clusterName,
                      event.getMembers().size()));

    }

    @Override
    public void memberRemoved(
        final MembershipEvent event) {

      log
          .info(
              ElectionCacheMessages
                  .memberLeft(
                      event.getMember().getAddress().toString(),
                      clusterName,
                      event.getMembers().size()));

    }

  }

}
