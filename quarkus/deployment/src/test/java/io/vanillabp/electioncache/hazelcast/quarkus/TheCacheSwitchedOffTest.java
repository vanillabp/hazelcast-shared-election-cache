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
 * Holds what happens in an environment which should not form a cluster, like an installation with
 * one node, or the test of another project. The dependency stays, and the hints go back into the
 * in-memory cache of the platform. Each node keeps that cache for itself.
 * <p>
 * The application gets the in-memory cache of the platform as its bean. That also shows that no
 * member was started. The producer starts a member only on its way to building the other bean.
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

    // the producer of this extension starts the member, and it did not: the bean it
    // produced is the cache of the platform. The list of all Hazelcast instances of the
    // JVM is not checked on purpose. The applications of the other test classes of this
    // module run in the same JVM, so that list says nothing about this one
    assertThat(cacheOfTheApplication).isInstanceOf(InMemoryWorkflowAdapterCache.class);

    cacheOfTheApplication.put("election-cache-test-module", "ride", "1", "pea");
    assertThat(cacheOfTheApplication.get("election-cache-test-module", "ride", "1")).contains("pea");

  }

}
