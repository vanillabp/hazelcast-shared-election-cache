package io.vanillabp.electioncache.hazelcast.quarkus.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.pkg.steps.NativeBuild;
import io.vanillabp.electioncache.hazelcast.quarkus.runtime.HazelcastElectionCacheProducer;

/**
 * The deployment part of the Quarkus extension for the shared election cache. It registers the
 * producer of the cache bean. That is all the platform needs to use it instead of its own
 * in-memory default.
 */
class HazelcastElectionCacheProcessor {

  /**
   * Quarkus creates the class once per build to call the steps below. There is no state to set
   * up. What the steps produce is all that this extension adds to the build.
   */
  HazelcastElectionCacheProcessor() {

  }

  private static final String FEATURE = "vanillabp-hazelcast-election-cache";

  /**
   * What a native build is told. It is package-private, so the test can check this text and not a
   * copy of it.
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
   * Names the extension. That is how a developer sees in the build log that the shared election
   * cache is part of the application at all.
   *
   * @return What the build log names this extension
   */
  @BuildStep
  FeatureBuildItem feature() {

    return new FeatureBuildItem(FEATURE);

  }

  /**
   * Refuses a native build. Otherwise the build would make an image whose Hazelcast member fails
   * at startup. A native image cannot hold an embedded member, because it takes away reflection,
   * serialization and the class loading of Hazelcast. And this cache is a member, not a client,
   * because the nodes of the application are the cluster (see decision 1 in the repository's
   * DECISIONS.md).
   * <p>
   * The message names the way out, because a team which chose a native image had a reason for it.
   * They remove this dependency and leave the election cache to the in-memory default of
   * VanillaBP. That costs one round of asking the adapters per node, and nothing else.
   * <p>
   * No test covers the refusal. This repository builds no native image, so nobody here runs such
   * a build. A test DOES hold the message, because that is the part somebody has to act on.
   *
   * @param errors Where the refusal is handed to Quarkus, which ends the build with it
   */
  @BuildStep(onlyIf = NativeBuild.class)
  void refuseANativeImage(
      final BuildProducer<ValidationErrorBuildItem> errors) {

    errors.produce(new ValidationErrorBuildItem(new IllegalStateException(NATIVE_IMAGE_REFUSAL)));

  }

}
