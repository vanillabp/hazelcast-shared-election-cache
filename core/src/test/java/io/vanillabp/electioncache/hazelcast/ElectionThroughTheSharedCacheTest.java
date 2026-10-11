package io.vanillabp.electioncache.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vanillabp.integration.adapter.migration.processservice.WorkflowLocator;
import io.vanillabp.integration.adapter.migration.processservice.WorkflowLocator.Patience;
import io.vanillabp.integration.adapter.spi.MigratableProcessService;
import io.vanillabp.integration.adapter.spi.WorkflowAwareness;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * Holds the election itself, which is what a hint is for. The locator of the platform asks the
 * adapter which a hint names before it asks anybody else. With this cache, the hint of one node
 * steers the election of another node.
 * <p>
 * The adapters are test doubles on purpose. The test is not about what a BPMS answers. It is
 * about which BPMS is asked first, and the cache which the two locators share decides that.
 */
@ExtendWith(SuppressOutputExtension.class)
public class ElectionThroughTheSharedCacheTest {

  private static final String MODULE = "taxi-ride";

  private static final String PROCESS = "ride";

  private static TestCluster cluster;

  private static MigratableProcessService<String> camunda7;

  private static MigratableProcessService<String> camunda8;

  private static List<MigratableProcessService<String>> prioritizedAdapters;

  @BeforeAll
  @SuppressWarnings("unchecked")
  public static void startTwoNodes() {

    cluster = TestCluster
        .ofTwoNodes("election-through-the-shared-cache-test", Duration.ofHours(1), Duration.ofMinutes(5));

    camunda7 = mock(MigratableProcessService.class);
    camunda8 = mock(MigratableProcessService.class);
    when(camunda7.getAdapterId()).thenReturn("camunda7");
    when(camunda8.getAdapterId()).thenReturn("camunda8");
    // the order the application configured: without a hint, Camunda 7 is asked first
    prioritizedAdapters = List.of(camunda7, camunda8);

  }

  @AfterAll
  public static void stopTwoNodes() {

    cluster.close();

  }

  @Test
  @DisplayName("A node which never saw the workflow asks the BPMS the other node elected")
  public void theHintOfOneNodeRoutesTheElectionOfAnother() {

    // the node which started the workflow knows where it went, without asking anybody
    new WorkflowLocator(MODULE, PROCESS, cluster.nodeA()).remember("42", "camunda8");

    final var asked = new ArrayList<String>();

    final var location = new WorkflowLocator(MODULE, PROCESS, cluster.nodeB())
        .locate(prioritizedAdapters, probe(asked), "42", "workflow of aggregate '42'", Patience.NONE);

    assertThat(location.awareness()).isEqualTo(WorkflowAwareness.ACTIVE);
    assertThat(location.adapter().getAdapterId()).isEqualTo("camunda8");
    // only the adapter the hint named was asked. Camunda 7 was never asked, and that
    // is what the shared cache saves
    assertThat(asked).containsExactly("camunda8");

  }

  @Test
  @DisplayName("A walk without a hint elects and leaves a hint the whole cluster has")
  public void aWalkFillsTheCacheOfTheCluster() {

    final var asked = new ArrayList<String>();

    final var location = new WorkflowLocator(MODULE, PROCESS, cluster.nodeA())
        .locate(prioritizedAdapters, probe(asked), "43", "workflow of aggregate '43'", Patience.NONE);

    assertThat(location.adapter().getAdapterId()).isEqualTo("camunda8");
    assertThat(asked).containsExactly("camunda7", "camunda8");

    // the round which asked the adapters wrote the answer down, and the other node reads it
    final var askedByTheOtherNode = new ArrayList<String>();
    new WorkflowLocator(MODULE, PROCESS, cluster.nodeB())
        .locate(prioritizedAdapters, probe(askedByTheOtherNode), "43", "workflow of aggregate '43'", Patience.NONE);

    assertThat(askedByTheOtherNode).containsExactly("camunda8");

  }

  @Test
  @DisplayName("A hint which turned out to be wrong costs one walk and is repaired for everybody")
  public void aStaleHintIsRepairedForTheCluster() {

    new WorkflowLocator(MODULE, PROCESS, cluster.nodeA()).remember("44", "camunda7");

    final var asked = new ArrayList<String>();

    final var location = new WorkflowLocator(MODULE, PROCESS, cluster.nodeB())
        .locate(prioritizedAdapters, probe(asked), "44", "workflow of aggregate '44'", Patience.NONE);

    // the adapter of the hint was asked first and answered "unknown". Then asking the
    // adapters in turn found the one which holds the workflow
    assertThat(location.adapter().getAdapterId()).isEqualTo("camunda8");
    assertThat(asked).startsWith("camunda7");

    final var askedAgain = new ArrayList<String>();
    new WorkflowLocator(MODULE, PROCESS, cluster.nodeA())
        .locate(prioritizedAdapters, probe(askedAgain), "44", "workflow of aggregate '44'", Patience.NONE);

    assertThat(askedAgain).containsExactly("camunda8");

  }

  /**
   * How the adapters answer in this test. Camunda 8 holds every workflow, Camunda 7 holds none.
   * Every adapter which is asked adds itself to the given list.
   */
  private static Function<MigratableProcessService<String>, WorkflowAwareness> probe(
      final List<String> asked) {

    return adapter -> {
      asked.add(adapter.getAdapterId());
      return "camunda8".equals(adapter.getAdapterId())
          ? WorkflowAwareness.ACTIVE
          : WorkflowAwareness.UNKNOWN_TO_BPMS;
    };

  }

}
