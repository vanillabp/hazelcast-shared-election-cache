package io.vanillabp.electioncache.hazelcast.quarkus.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * Holds what a native build is told. The refusal itself is not tested. This repository builds no
 * native image, so nobody here runs the build in which the step would fire. But the sentence
 * somebody has to act on is tested. It is the only thing which keeps a team from an image whose
 * member fails at startup.
 */
@ExtendWith(SuppressOutputExtension.class)
public class NativeImageRefusalTest {

  @Test
  @DisplayName("The refusal says why, and names the way out")
  public void theRefusalNamesTheWayOut() {

    assertThat(HazelcastElectionCacheProcessor.NATIVE_IMAGE_REFUSAL)
        .contains("cannot be built into a native image")
        .contains("embedded Hazelcast MEMBER")
        .contains("JVM mode")
        .contains("io.vanillabp:hazelcast-shared-election-cache-quarkus")
        .contains("one probing walk per node");

  }

}
