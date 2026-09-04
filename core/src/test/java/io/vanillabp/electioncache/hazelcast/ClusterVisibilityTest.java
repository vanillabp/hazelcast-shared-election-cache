package io.vanillabp.electioncache.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vanillabp.integration.test.utils.CapturedOutput;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * Whether the log lets somebody tell "the cache is shared" from "the cache exists". A
 * member which found nobody works, answers every call and shares nothing, so the size
 * of the cluster is the one number which has to be readable without a debugger.
 */
@ExtendWith(SuppressOutputExtension.class)
public class ClusterVisibilityTest {

  @Test
  @DisplayName("A node which is alone says so at startup and keeps saying it")
  public void aNodeWhichIsAloneSaysSo(
      final CapturedOutput output) {

    final var properties = TestCluster
        .properties("cluster-visibility-alone-test", TestCluster.seedRange());
    // often enough for a test to see it twice
    properties.setAloneReminderInterval(Duration.ofMillis(100));

    final var member = ElectionCacheMember
        .start(properties, "taxi-ride", Duration.ofHours(1), Duration.ofMinutes(5), null);

    try {
      // the startup line names the cluster and its size
      assertThat(output.getAll()).contains("cluster-visibility-alone-test");
      assertThat(output.getAll()).contains("1 member(s)");

      TestCluster
          .await(
              () -> countOf(output, "consists of this node alone") >= 2,
              "the warning about being alone to be repeated");
    } finally {
      member.close();
    }

  }

  @Test
  @DisplayName("A member joining and leaving is logged with the size after it")
  public void everyMembershipChangeIsLogged(
      final CapturedOutput output) {

    final var seeds = TestCluster.seedRange();

    final var first = ElectionCacheMember
        .start(
            TestCluster.properties("cluster-visibility-membership-test", seeds),
            "taxi-ride",
            Duration.ofHours(1),
            Duration.ofMinutes(5),
            null);

    try {
      final var second = ElectionCacheMember
          .start(
              TestCluster.properties("cluster-visibility-membership-test", seeds),
              "taxi-ride",
              Duration.ofHours(1),
              Duration.ofMinutes(5),
              null);

      TestCluster
          .await(
              () -> output.getAll().contains("joined Hazelcast cluster"),
              "the line about the second member joining");
      assertThat(output.getAll()).contains("2 member(s)");

      second.close();

      TestCluster
          .await(
              () -> output.getAll().contains("left Hazelcast cluster"),
              "the line about the second member leaving");
      assertThat(output.getAll()).contains("one probing walk");
    } finally {
      first.close();
    }

  }

  private static int countOf(
      final CapturedOutput output,
      final String sentence) {

    return output.getAll().split(sentence, -1).length - 1;

  }

}
