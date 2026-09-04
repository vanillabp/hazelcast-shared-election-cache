package io.vanillabp.electioncache.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.hazelcast.core.Hazelcast;

import io.vanillabp.integration.test.utils.CapturedOutput;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * Whose Hazelcast the hints live in. A project which already runs Hazelcast has an
 * instance configured, and a second member in the same JVM would be waste plus a second
 * cluster to reason about - so the cache moves in, and it leaves that instance running
 * on the way out, because shutting somebody else's Hazelcast down would take their
 * caches with it.
 */
@ExtendWith(SuppressOutputExtension.class)
public class ElectionCacheMemberTest {

  @Test
  @DisplayName("The instance the application provides is used, and survives our shutdown")
  public void theApplicationsInstanceIsUsedAndSurvives(
      final CapturedOutput output) {

    final var applicationsInstance = Hazelcast
        .newHazelcastInstance(
            ElectionCacheMemberConfig
                .of(
                    aloneOnItsOwnPort(),
                    "the-applications-own-cluster",
                    ElectionCacheMemberTest.class.getClassLoader()));

    try {
      final var member = ElectionCacheMember
          .start(
              aloneOnItsOwnPort(),
              "taxi-ride",
              Duration.ofHours(1),
              Duration.ofMinutes(5),
              applicationsInstance);

      assertThat(member.clusterName()).isEqualTo("the-applications-own-cluster");
      assertThat(member.instance()).isSameAs(applicationsInstance);
      assertThat(output.getAll()).contains("instance the application provides");

      member.cache().put("taxi-ride", "ride", "1", "camunda8");
      assertThat(member.cache().get("taxi-ride", "ride", "1")).contains("camunda8");

      member.close();

      // the application's Hazelcast is still there, and so are the hints in it
      assertThat(applicationsInstance.getLifecycleService().isRunning()).isTrue();
      assertThat(applicationsInstance.getMap("vanillabp-election-cache").get("taxi-ride|ride|1"))
          .isEqualTo("camunda8");
    } finally {
      applicationsInstance.shutdown();
    }

  }

  @Test
  @DisplayName("An application which wants a cluster of its own for the hints gets one")
  public void anInstanceOfOurOwnIsStartedWhereItIsAskedFor() {

    final var applicationsInstance = Hazelcast
        .newHazelcastInstance(
            ElectionCacheMemberConfig
                .of(
                    aloneOnItsOwnPort(),
                    "the-applications-own-cluster",
                    ElectionCacheMemberTest.class.getClassLoader()));

    try {
      final var properties = aloneOnItsOwnPort();
      properties.setUseExistingInstance(false);

      final var member = ElectionCacheMember
          .start(properties, "taxi-ride", Duration.ofHours(1), Duration.ofMinutes(5), applicationsInstance);

      try {
        assertThat(member.clusterName()).isEqualTo(properties.getClusterName());
        assertThat(member.instance()).isNotSameAs(applicationsInstance);
      } finally {
        member.close();
      }

      assertThat(applicationsInstance.getLifecycleService().isRunning()).isTrue();
    } finally {
      applicationsInstance.shutdown();
    }

  }

  @Test
  @DisplayName("A member we started is shut down with the application")
  public void aMemberOfOurOwnIsShutDown() {

    final var member = ElectionCacheMember
        .start(aloneOnItsOwnPort(), "taxi-ride", Duration.ofHours(1), Duration.ofMinutes(5), null);

    final var instance = member.instance();
    assertThat(instance.getLifecycleService().isRunning()).isTrue();

    member.close();

    assertThat(instance.getLifecycleService().isRunning()).isFalse();

  }

  /**
   * A member which looks for nobody but itself: a port of its own, and its own address
   * as the only seed.
   */
  private static HazelcastElectionCacheProperties aloneOnItsOwnPort() {

    return TestCluster.properties("election-cache-member-test", TestCluster.seedRange());

  }

}
