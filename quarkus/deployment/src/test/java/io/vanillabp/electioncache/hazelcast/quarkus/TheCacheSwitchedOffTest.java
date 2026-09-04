package io.vanillabp.electioncache.hazelcast.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

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
 * The bean the application gets is the platform's in-memory cache, which is also the
 * proof that no member was started: starting one is what the producer does on its way to
 * building the other bean.
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

    // the producer of this extension is what starts a member, and it did not: the bean it
    // produced is the platform's own cache. The JVM-wide list of Hazelcast instances is
    // deliberately not asserted here - the applications of the other test classes of this
    // module live in the same JVM, so that list says nothing about this one
    assertThat(cacheOfTheApplication).isInstanceOf(InMemoryWorkflowAdapterCache.class);

    cacheOfTheApplication.put("election-cache-test-module", "ride", "1", "pea");
    assertThat(cacheOfTheApplication.get("election-cache-test-module", "ride", "1")).contains("pea");

  }

}
