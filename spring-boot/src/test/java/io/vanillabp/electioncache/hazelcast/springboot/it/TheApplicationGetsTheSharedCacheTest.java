package io.vanillabp.electioncache.hazelcast.springboot.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.vanillabp.electioncache.hazelcast.ElectionCacheMember;
import io.vanillabp.electioncache.hazelcast.HazelcastElectionCacheProperties;
import io.vanillabp.integration.spi.WorkflowAdapterCache;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * The acceptance test of this repository on Spring Boot: an application which boots
 * VanillaBP with a BPMS adapter and this dependency gets the shared cache, and the
 * in-memory default of the platform steps back without anybody wiring anything.
 * <p>
 * That the hints really leave this node is proven with a second member started inside
 * the test: what the application's bean writes is what that member reads.
 */
@SpringBootTest(
    classes = ElectionCacheTestApplication.class,
    properties = {
        "spring.application.name=taxi-ride", "vanillabp.prioritized-adapters=pea", "vanillabp.adapters.pea.type=process-engine-api", "vanillabp.adapters.pea.name-clash-avoidance=none", "vanillabp.workflow-modules.election-cache-test-module.adapters.pea.resources-location=classpath*:election-cache-test-module/processes/none", "vanillabp.workflow-adapter-cache.hazelcast.cluster-name=spring-application-test", "vanillabp.workflow-adapter-cache.hazelcast.discovery=members", "vanillabp.workflow-adapter-cache.hazelcast.members=127.0.0.1:15801,127.0.0.1:15802", "vanillabp.workflow-adapter-cache.hazelcast.port=15801", "vanillabp.workflow-adapter-cache.hazelcast.alone-reminder-interval=PT1H"
    })
@ExtendWith(SuppressOutputExtension.class)
public class TheApplicationGetsTheSharedCacheTest {

  private static final String MODULE = "election-cache-test-module";

  private static final String PROCESS = "ride";

  @Autowired
  private WorkflowAdapterCache cacheOfTheApplication;

  @Test
  @DisplayName("The application's election cache is the Hazelcast one, shared with the next node")
  public void theApplicationSharesItsElections() {

    // the bean this repository contributes, and not the platform's in-memory default
    assertThat(cacheOfTheApplication.getClass().getSimpleName()).isEqualTo("ManagedElectionCache");

    final var theNextNode = ElectionCacheMember
        .start(
            propertiesOfTheNextNode(),
            "taxi-ride",
            Duration.ofHours(1),
            Duration.ofMinutes(5),
            null);

    try {
      cacheOfTheApplication.put(MODULE, PROCESS, "1", "pea");

      assertThat(theNextNode.cache().get(MODULE, PROCESS, "1")).contains("pea");

      theNextNode.cache().put(MODULE, PROCESS, "2", "pea");

      assertThat(cacheOfTheApplication.get(MODULE, PROCESS, "2")).contains("pea");
    } finally {
      theNextNode.close();
    }

  }

  /**
   * The second node of the same application: the same cluster, the same seeds, the
   * other port.
   */
  private static HazelcastElectionCacheProperties propertiesOfTheNextNode() {

    final var properties = new HazelcastElectionCacheProperties();
    properties.setClusterName("spring-application-test");
    properties.setDiscovery(HazelcastElectionCacheProperties.Discovery.MEMBERS);
    properties.setMembers(java.util.List.of("127.0.0.1:15801", "127.0.0.1:15802"));
    properties.setPort(15802);
    properties.setAloneReminderInterval(Duration.ofHours(1));
    return properties;

  }

}
