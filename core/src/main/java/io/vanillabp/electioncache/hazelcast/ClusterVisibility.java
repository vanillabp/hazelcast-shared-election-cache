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
 * What the log says about the cluster, which is the only place an operator can read
 * whether the cache is shared at all.
 * <p>
 * A member which finds nobody forms a cluster of one, and everything looks fine: no
 * exception, no failed request, an application behaving exactly as it did with the
 * in-memory default. So the size is stated at startup, every membership change is
 * logged with the size after it, and a member which is alone repeats that for as long
 * as it stays alone. Telling "the cache is shared" from "the cache exists" must not
 * require a debugger.
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
   * Says where the cluster stands and starts watching it.
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
    // starts immediately: a node which comes up alone should say so in the same breath
    // as the line above, not five minutes later
    aloneReminder
        .scheduleWithFixedDelay(
            this::warnWhileAlone,
            0,
            properties.getAloneReminderInterval().toMillis(),
            TimeUnit.MILLISECONDS);

  }

  /**
   * Warns while this member is the whole cluster. It is a repeated warning on purpose:
   * this is the one state which is both wrong and invisible.
   */
  private void warnWhileAlone() {

    try {
      if (instance.getCluster().getMembers().size() > 1) {
        return;
      }
      log.warn(ElectionCacheMessages.alone(clusterName, properties));
    } catch (final Exception e) {
      // a member which is shutting down answers nothing, and a reminder is not worth a
      // stack trace on the way out
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
   * Every change of the membership, with the size after it.
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
