package io.vanillabp.electioncache.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vanillabp.integration.spi.WorkflowAdapterCache;
import io.vanillabp.integration.test.utils.CapturedOutput;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * The behaviour which makes an entry of this cache a hint rather than a dependency: with
 * Hazelcast gone, every method answers as if the cache were empty and none of them
 * throws.
 * <p>
 * This is the test the whole repository stands on. An application whose cache throws on
 * a network partition has turned a shortcut into an outage, and nothing about that is
 * visible until the partition happens (see decision 2 in the repository's
 * DECISIONS.md).
 */
@ExtendWith(SuppressOutputExtension.class)
public class UnreachableHazelcastTest {

  private static final String MODULE = "taxi-ride";

  private static final String PROCESS = "ride";

  @Test
  @DisplayName("With Hazelcast gone the cache is empty, quiet and never in the way")
  public void aGoneHazelcastAnswersLikeAnEmptyCache(
      final CapturedOutput output) {

    final WorkflowAdapterCache cache;

    final var member = ElectionCacheMember
        .start(
            TestCluster.properties("unreachable-hazelcast-test", TestCluster.seedRange()),
            null,
            Duration.ofHours(1),
            Duration.ofMinutes(5),
            null);
    try {
      cache = member.cache();
      cache.put(MODULE, PROCESS, "1", "camunda8");
      assertThat(cache.get(MODULE, PROCESS, "1")).contains("camunda8");
    } finally {
      // not shutdown(): a member which is killed is what a partition looks like from
      // the inside, and it is the case an orderly shutdown would hide
      member.instance().getLifecycleService().terminate();
    }

    assertThat(cache.get(MODULE, PROCESS, "1")).isEmpty();

    assertThatCode(() -> cache.put(MODULE, PROCESS, "2", "camunda8")).doesNotThrowAnyException();
    assertThatCode(() -> cache.putEnded(MODULE, PROCESS, "2", "camunda8")).doesNotThrowAnyException();
    assertThatCode(() -> cache.invalidate(MODULE, PROCESS, "2")).doesNotThrowAnyException();

    final var logged = output.getAll();
    assertThat(logged).contains("as if it were empty");
    assertThat(logged).contains("Workflow operations are unaffected");

    member.close();

  }

}
