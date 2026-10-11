package io.vanillabp.electioncache.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.hazelcast.core.HazelcastInstanceNotActiveException;

import io.vanillabp.integration.test.utils.CapturedOutput;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * Holds how much a cache logs when it cannot reach Hazelcast. A node whose cluster is gone fails
 * every single call. A line per call would hide the cause under its symptoms. So the first
 * failure of an interval is logged, and the rest are counted. The next line then says how long
 * this has been going on.
 */
@ExtendWith(SuppressOutputExtension.class)
public class ElectionCacheFailuresTest {

  @Test
  @DisplayName("The first failure is logged with its cause, the ones behind it are counted")
  public void theFirstFailureIsLoggedAndTheRestCounted(
      final CapturedOutput output) {

    final var linesBefore = countOf(output, "as if it were empty");

    final var failures = new ElectionCacheFailures(Duration.ofHours(1));

    failures.failed("read the hint of a workflow", new HazelcastInstanceNotActiveException());
    failures.failed("read the hint of a workflow", new HazelcastInstanceNotActiveException());
    failures.failed("store the hint of a workflow", new HazelcastInstanceNotActiveException());

    assertThat(failures.suppressedSinceLastLine()).isEqualTo(2);

    final var logged = output.getAll();
    assertThat(logged).contains("read the hint of a workflow");
    assertThat(logged).contains(HazelcastInstanceNotActiveException.class.getSimpleName());
    assertThat(countOf(output, "as if it were empty")).isEqualTo(linesBefore + 1);

  }

  @Test
  @DisplayName("The next interval reports how many failures it did not report")
  public void theNextLineCountsWhatItSuppressed(
      final CapturedOutput output) throws InterruptedException {

    final var interval = Duration.ofMillis(20);
    final var failures = new ElectionCacheFailures(interval);

    failures.failed("read the hint of a workflow", new HazelcastInstanceNotActiveException());
    failures.failed("read the hint of a workflow", new HazelcastInstanceNotActiveException());
    failures.failed("read the hint of a workflow", new HazelcastInstanceNotActiveException());

    assertThat(failures.suppressedSinceLastLine()).isEqualTo(2);

    Thread.sleep(interval.toMillis() * 2);

    failures.failed("read the hint of a workflow", new HazelcastInstanceNotActiveException());

    assertThat(output.getAll()).contains("2 further failure(s)");
    assertThat(failures.suppressedSinceLastLine()).isZero();

  }

  /**
   * How often a sentence was logged. It is counted, because finding it is not enough. The
   * captured output also holds what the tests before this one in the same class printed.
   */
  private static int countOf(
      final CapturedOutput output,
      final String sentence) {

    return output.getAll().split(sentence, -1).length - 1;

  }

}
