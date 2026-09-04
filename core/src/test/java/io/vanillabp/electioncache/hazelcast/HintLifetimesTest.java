package io.vanillabp.electioncache.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * The two lifetimes, in a cluster whose short one is short enough to watch: the hint of
 * a living workflow is worth keeping, the hint of one which ended can never become
 * useful again and has no business occupying a place in infrastructure the application
 * pays for.
 * <p>
 * The lifetimes are the platform's properties
 * (<code>vanillabp.workflow-adapter-cache.time-to-live</code> and
 * <code>.ended-time-to-live</code>), and they are per entry rather than per map for
 * exactly this reason: one map holds both kinds.
 */
@ExtendWith(SuppressOutputExtension.class)
public class HintLifetimesTest {

  private static final String MODULE = "taxi-ride";

  private static final String PROCESS = "ride";

  private static final Duration ENDED_LIFETIME = Duration.ofSeconds(1);

  private static TestCluster cluster;

  @BeforeAll
  public static void startTwoNodes() {

    cluster = TestCluster.ofTwoNodes("hint-lifetimes-test", Duration.ofHours(1), ENDED_LIFETIME);

  }

  @AfterAll
  public static void stopTwoNodes() {

    cluster.close();

  }

  @Test
  @DisplayName("The hint of a workflow which ended leaves after the short lifetime")
  public void anEndedWorkflowLetsGoOfItsHint() {

    cluster.nodeA().putEnded(MODULE, PROCESS, "ended", "camunda8");

    assertThat(cluster.nodeB().get(MODULE, PROCESS, "ended")).contains("camunda8");

    TestCluster
        .await(
            () -> cluster.nodeB().get(MODULE, PROCESS, "ended").isEmpty(),
            "the hint of the ended workflow to expire after "
                + ENDED_LIFETIME);

  }

  @Test
  @DisplayName("The hint of a living workflow outlives the short lifetime by far")
  public void aLivingWorkflowKeepsItsHint() throws InterruptedException {

    cluster.nodeA().put(MODULE, PROCESS, "living", "camunda8");

    Thread.sleep(ENDED_LIFETIME.toMillis() * 3);

    assertThat(cluster.nodeB().get(MODULE, PROCESS, "living")).contains("camunda8");

  }

  @Test
  @DisplayName("A mark shortens the hint it found")
  public void aMarkShortensAHintWhichWasThere() {

    cluster.nodeA().put(MODULE, PROCESS, "first-living-then-ended", "camunda8");

    cluster.nodeB().putEnded(MODULE, PROCESS, "first-living-then-ended", "camunda8");

    TestCluster
        .await(
            () -> cluster.nodeA().get(MODULE, PROCESS, "first-living-then-ended").isEmpty(),
            "the marked hint to expire after "
                + ENDED_LIFETIME
                + " instead of after an hour");

  }

}
