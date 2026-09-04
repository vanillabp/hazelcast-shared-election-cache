package io.vanillabp.electioncache.hazelcast.quarkus.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * What a native build is told. The refusal itself is not tested - this repository builds
 * no native image, so whether the build step fires is a claim about a build nobody here
 * runs - but the sentence somebody has to act on is, because it is the only thing
 * standing between a team and an image whose member fails at startup.
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
