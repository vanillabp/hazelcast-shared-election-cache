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
 * The reason this repository exists: a hint one node wrote is a hint every other node
 * has. Two members in one JVM are enough to prove it, and everything else about the
 * cache is a rule which only becomes visible with two of them - which adapter a mark
 * may overwrite, and what a node reads after another one dropped an entry.
 */
@ExtendWith(SuppressOutputExtension.class)
public class SharedElectionCacheTest {

  private static final String MODULE = "taxi-ride";

  private static final String PROCESS = "ride";

  private static TestCluster cluster;

  @BeforeAll
  public static void startTwoNodes() {

    cluster = TestCluster
        .ofTwoNodes("shared-election-cache-test", Duration.ofHours(1), Duration.ofMinutes(5));

  }

  @AfterAll
  public static void stopTwoNodes() {

    cluster.close();

  }

  @Test
  @DisplayName("What one node elected, another node reads")
  public void aHintCrossesTheCluster() {

    cluster.nodeA().put(MODULE, PROCESS, "1", "camunda8");

    assertThat(cluster.nodeB().get(MODULE, PROCESS, "1")).contains("camunda8");

  }

  @Test
  @DisplayName("A hint nobody wrote is missing rather than wrong")
  public void anUnknownWorkflowHasNoHint() {

    assertThat(cluster.nodeB().get(MODULE, PROCESS, "nobody-elected-this")).isEmpty();

  }

  @Test
  @DisplayName("A hint dropped as stale is gone for the whole cluster")
  public void invalidationCrossesTheCluster() {

    cluster.nodeA().put(MODULE, PROCESS, "2", "camunda7");
    assertThat(cluster.nodeB().get(MODULE, PROCESS, "2")).contains("camunda7");

    cluster.nodeB().invalidate(MODULE, PROCESS, "2");

    assertThat(cluster.nodeA().get(MODULE, PROCESS, "2")).isEmpty();

    // and dropping an entry which is not there is a no-op, not a failure
    cluster.nodeB().invalidate(MODULE, PROCESS, "2");

  }

  @Test
  @DisplayName("The end of a workflow leaves a hint naming another adapter alone")
  public void aMarkDoesNotOverwriteAnotherAdapter() {

    // the key names the aggregate and not the instance, so a second workflow on the
    // same aggregate wrote this hint - and it is the newer knowledge of the two
    cluster.nodeA().put(MODULE, PROCESS, "3", "camunda8");

    cluster.nodeB().putEnded(MODULE, PROCESS, "3", "camunda7");

    assertThat(cluster.nodeA().get(MODULE, PROCESS, "3")).contains("camunda8");

  }

  @Test
  @DisplayName("The end of a workflow is marked where the hint names the same adapter")
  public void aMarkKeepsTheHintReadable() {

    cluster.nodeA().put(MODULE, PROCESS, "4", "camunda8");

    cluster.nodeB().putEnded(MODULE, PROCESS, "4", "camunda8");

    // the mark is not a deletion: an operation arriving after the end still asks the
    // adapter which held the workflow and becomes a warned no-op there
    assertThat(cluster.nodeA().get(MODULE, PROCESS, "4")).contains("camunda8");

  }

  @Test
  @DisplayName("The end of a workflow nobody elected is written like any other hint")
  public void aMarkWithoutAHintIsAHint() {

    cluster.nodeA().putEnded(MODULE, PROCESS, "5", "camunda8");

    assertThat(cluster.nodeB().get(MODULE, PROCESS, "5")).contains("camunda8");

  }

}
