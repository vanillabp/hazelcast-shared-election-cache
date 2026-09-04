package io.vanillabp.electioncache.hazelcast.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.hazelcast.core.Hazelcast;

import io.quarkus.test.QuarkusExtensionTest;
import io.vanillabp.integration.adapter.migration.processservice.InMemoryWorkflowAdapterCache;
import io.vanillabp.integration.spi.WorkflowAdapterCache;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import jakarta.inject.Inject;

/**
 * An environment which should not form a cluster: a single-node installation, or a test
 * of somebody else's. The dependency stays where it is and the hints go back into the
 * in-memory cache of the platform, which every node keeps for itself.
 * <p>
 * What this asserts is that no Hazelcast is started at all - a switch which only stops
 * the cache from being used would leave the member, its threads and its port behind.
 */
@ExtendWith(SuppressOutputExtension.class)
public class TheCacheSwitchedOffTest {

  @RegisterExtension
  static final QuarkusExtensionTest theApplication = new QuarkusExtensionTest()
      .setArchiveProducer(() -> ShrinkWrap
          .create(JavaArchive.class)
          .addAsResource("switched-off/application.yaml", "application.yaml")
          .addAsResource("META-INF/workflow-module", "META-INF/workflow-module"));

  @Inject
  WorkflowAdapterCache cacheOfTheApplication;

  @Test
  @DisplayName("The hints go back into the in-memory cache, and no member is started")
  public void theInMemoryCacheTakesOver() {

    assertThat(cacheOfTheApplication).isInstanceOf(InMemoryWorkflowAdapterCache.class);
    assertThat(Hazelcast.getAllHazelcastInstances()).isEmpty();

    cacheOfTheApplication.put("election-cache-test-module", "ride", "1", "pea");
    assertThat(cacheOfTheApplication.get("election-cache-test-module", "ride", "1")).contains("pea");

  }

}
