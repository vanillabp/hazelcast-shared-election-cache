package io.vanillabp.electioncache.hazelcast.quarkus.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.pkg.steps.NativeBuild;
import io.vanillabp.electioncache.hazelcast.quarkus.runtime.HazelcastElectionCacheProducer;

/**
 * Quarkus extension deployment of the shared election cache: it registers the producer
 * of the cache bean, which is all the platform needs to prefer it over its own in-memory
 * default.
 */
class HazelcastElectionCacheProcessor {

  private static final String FEATURE = "vanillabp-hazelcast-election-cache";

  /**
   * What a native build is told. Package-private so the test asserting it can read the
   * text rather than a copy of it.
   */
  static final String NATIVE_IMAGE_REFUSAL = """
      The VanillaBP election cache on Hazelcast cannot be built into a native image: it runs an \
      embedded Hazelcast MEMBER, because the nodes of the application are the cluster. Build this \
      application in JVM mode, or remove the dependency \
      'io.vanillabp:hazelcast-shared-election-cache-quarkus' and leave the election cache to \
      VanillaBP's in-memory default - elections are then not shared between the nodes, which costs \
      one probing walk per node and nothing else.""";

  /**
   * Registers the producer contributing the cache.
   *
   * @return The bean registration build item
   */
  @BuildStep
  AdditionalBeanBuildItem registerTheProducer() {

    return AdditionalBeanBuildItem
        .builder()
        .addBeanClass(HazelcastElectionCacheProducer.class)
        .setUnremovable()
        .build();

  }

  /**
   * @return What the build log names this extension
   */
  @BuildStep
  FeatureBuildItem feature() {

    return new FeatureBuildItem(FEATURE);

  }

  /**
   * Refuses a native build rather than letting it produce an image whose Hazelcast
   * member fails at startup. An embedded member is not what a native image can hold
   * (its reflection, its serialization and its own class loading are what such an image
   * takes away), and this cache is a member rather than a client because the nodes of
   * the application are the cluster (see decision 1 in the repository's DECISIONS.md).
   * <p>
   * The message names the way out, because a team which chose a native image did so for
   * a reason: remove this dependency and leave the election cache to VanillaBP's
   * in-memory default, which costs one probing walk per node and nothing else.
   * <p>
   * Not covered by a test: this repository builds no native image, so the refusal is a
   * claim about a build nobody here runs. What IS held by a test is the message, which
   * is the part somebody has to be able to act on.
   */
  @BuildStep(onlyIf = NativeBuild.class)
  void refuseANativeImage(
      final BuildProducer<ValidationErrorBuildItem> errors) {

    errors.produce(new ValidationErrorBuildItem(new IllegalStateException(NATIVE_IMAGE_REFUSAL)));

  }

}
