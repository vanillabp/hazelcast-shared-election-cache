package io.vanillabp.electioncache.hazelcast.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.vanillabp.electioncache.hazelcast.ElectionCacheMember;
import io.vanillabp.electioncache.hazelcast.HazelcastElectionCache;
import io.vanillabp.electioncache.hazelcast.HazelcastElectionCacheProperties;
import io.vanillabp.integration.spi.WorkflowAdapterCache;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import jakarta.inject.Inject;

/**
 * The acceptance test of this repository on Quarkus. It holds that an application which starts
 * VanillaBP with a BPMS adapter and this extension gets the shared cache. The in-memory default
 * of the platform steps back, and nobody has to wire anything.
 * <p>
 * A second member, started inside the test, shows that the hints really leave this node. What the
 * bean of the application writes, that member reads. The test checks the lifetimes as well. They
 * are properties of the platform, not of this repository, and reading the wrong ones would show no
 * visible error at all.
 */
@ExtendWith(SuppressOutputExtension.class)
public class TheApplicationGetsTheSharedCacheTest {

  private static final String MODULE = "election-cache-test-module";

  private static final String PROCESS = "ride";

  @RegisterExtension
  static final QuarkusExtensionTest theApplication = new QuarkusExtensionTest()
      .setArchiveProducer(() -> ShrinkWrap
          .create(JavaArchive.class)
          .addAsResource("application.yaml")
          .addAsResource("META-INF/workflow-module", "META-INF/workflow-module"));

  @Inject
  WorkflowAdapterCache cacheOfTheApplication;

  @Test
  @DisplayName("The application's election cache is the Hazelcast one, shared with the next node")
  public void theApplicationSharesItsElections() {

    // the bean of this extension, not the in-memory default of the platform
    assertThat(cacheOfTheApplication).isInstanceOf(HazelcastElectionCache.class);

    final var theNextNode = ElectionCacheMember
        .start(propertiesOfTheNextNode(), "taxi-ride", Duration.ofHours(1), Duration.ofSeconds(1), null);

    try {
      cacheOfTheApplication.put(MODULE, PROCESS, "1", "pea");

      assertThat(theNextNode.cache().get(MODULE, PROCESS, "1")).contains("pea");

      theNextNode.cache().put(MODULE, PROCESS, "2", "pea");

      assertThat(cacheOfTheApplication.get(MODULE, PROCESS, "2")).contains("pea");
    } finally {
      theNextNode.close();
    }

  }

  @Test
  @DisplayName("The two lifetimes are the platform's properties, not ours")
  public void theLifetimesComeFromThePlatformsSection() throws InterruptedException {

    cacheOfTheApplication.put(MODULE, PROCESS, "living", "pea");
    cacheOfTheApplication.putEnded(MODULE, PROCESS, "ended", "pea");

    // 'ended-time-to-live' is a second in this application, 'time-to-live' an hour
    final var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (cacheOfTheApplication.get(MODULE, PROCESS, "ended").isPresent()) {
      assertThat(System.nanoTime()).isLessThan(deadline);
      Thread.sleep(50);
    }

    assertThat(cacheOfTheApplication.get(MODULE, PROCESS, "living")).contains("pea");

  }

  /**
   * The second node of the same application: the same cluster, the same seeds, the other port.
   */
  private static HazelcastElectionCacheProperties propertiesOfTheNextNode() {

    final var properties = new HazelcastElectionCacheProperties();
    properties.setClusterName("quarkus-application-test");
    properties.setDiscovery(HazelcastElectionCacheProperties.Discovery.MEMBERS);
    properties.setMembers(List.of("127.0.0.1:15811", "127.0.0.1:15812"));
    properties.setPort(15812);
    properties.setAloneReminderInterval(Duration.ofHours(1));
    return properties;

  }

}
