package io.vanillabp.electioncache.hazelcast.quarkus.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.vanillabp.electioncache.hazelcast.HazelcastElectionCacheProperties;

/**
 * The OVERLAY of this cache on the shared <code>vanillabp.*</code> configuration tree. It holds
 * the keys under <code>vanillabp.workflow-adapter-cache.hazelcast</code>, inside the section
 * which the platform owns. A team which raises the lifetime of a hint and a team which points the
 * members at a Kubernetes service configure the same cache.
 * <p>
 * Quarkus cannot ignore the whole <code>vanillabp</code> prefix. A key which no registered
 * mapping knows stops the start. So this overlay is what makes these keys usable at all.
 * <p>
 * Never {@code @Inject} this mapping. Injecting it turns it into a STATIC-INIT mapping, and the
 * whole tree is then checked before the extensions registered their RUN_TIME overlays. Read it
 * through {@code ConfigProvider.getConfig().unwrap(SmallRyeConfig.class).getConfigMapping(...)}
 * instead, as {@link HazelcastElectionCacheProducer} does.
 * <p>
 * The defaults are the ones of {@link HazelcastElectionCacheProperties}. They are written twice
 * on purpose. A mapping cannot read a constant, and a default which differs between the platforms
 * would be worse than a literal written twice. A test of this Quarkus module compares them with
 * the core model. Spring Boot binds a subclass of the core model, so its defaults cannot differ.
 */
@StaticInitSafe
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "vanillabp")
public interface VanillaBpElectionCacheProperties {

  /**
   * The election-cache section of the platform. Only the part of this cache is modelled here.
   *
   * @return The section
   */
  WorkflowAdapterCacheOverlay workflowAdapterCache();

  /**
   * The part of <code>vanillabp.workflow-adapter-cache</code> this repository owns.
   */
  interface WorkflowAdapterCacheOverlay {

    /**
     * Where the hints live and how the nodes find each other.
     *
     * @return The Hazelcast settings
     */
    HazelcastOverlay hazelcast();

  }

  /**
   * The keys under <code>vanillabp.workflow-adapter-cache.hazelcast</code>. What each
   * one means is written once, in {@link HazelcastElectionCacheProperties}.
   */
  interface HazelcastOverlay {

    /**
     * Whether the election cache runs on Hazelcast at all. Switched off, the hints stay in the
     * in-memory cache of the platform, and each node keeps that cache for itself.
     *
     * @return Whether the election cache runs on Hazelcast at all
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * The name of the Hazelcast cluster which the nodes of this application form. If it is not
     * set, it is made from the name of the application. That keeps two applications on one
     * network apart.
     *
     * @return The name of the Hazelcast cluster. If it is not set, it is made from the name of
     *         the application
     */
    Optional<String> clusterName();

    /**
     * The name of the Hazelcast map holding the hints.
     *
     * @return The name of the map holding the hints
     */
    @WithDefault("vanillabp-election-cache")
    String mapName();

    /**
     * How a starting member finds the members already running: 'auto-detect',
     * 'multicast', 'members' or 'kubernetes'.
     *
     * @return How a starting member finds the members already running
     */
    @WithDefault("auto-detect")
    String discovery();

    /**
     * The addresses a starting member tries, as 'host' or 'host:port'. They are seeds, not a list
     * of all members. The member which answers hands over all the others, so not every node has
     * to be listed.
     *
     * @return The seed addresses a starting member tries, not the full membership
     */
    Optional<List<String>> members();

    /**
     * The port the members talk to each other on. It is not the HTTP port of the application,
     * and it has to be open between the nodes.
     *
     * @return The port the members talk to each other on
     */
    @WithDefault("5701")
    int port();

    /**
     * Whether a member whose port is taken tries the next ones.
     *
     * @return Whether a member whose port is taken tries the next ones
     */
    @WithDefault("true")
    boolean portAutoIncrement();

    /**
     * The multicast group. If it is not set, Hazelcast uses its own default.
     *
     * @return The multicast group. If it is not set, Hazelcast uses its own default
     */
    Optional<String> multicastGroup();

    /**
     * The multicast port. If it is not set, Hazelcast uses its own default.
     *
     * @return The multicast port. If it is not set, Hazelcast uses its own default
     */
    Optional<Integer> multicastPort();

    /**
     * The DNS name of a HEADLESS Kubernetes service in front of the pods. This way needs no
     * multicast, no address and no permission on the Kubernetes API.
     *
     * @return The DNS name of a headless Kubernetes service. This way needs no address and no
     *         permission
     */
    Optional<String> kubernetesServiceDns();

    /**
     * The name of a Kubernetes service whose endpoints are read through the Kubernetes API. That
     * needs a role which allows the pod to do so.
     *
     * @return The name of a Kubernetes service whose endpoints are read through the
     *         Kubernetes API
     */
    Optional<String> kubernetesServiceName();

    /**
     * The Kubernetes namespace of that service. If it is not set, the namespace of the pod is used.
     *
     * @return The namespace of that service. If it is not set, the namespace of the pod is used
     */
    Optional<String> kubernetesNamespace();

    /**
     * How many copies of each partition the cluster keeps. This decides about cost, not about
     * correctness. A hint which is gone costs one round of asking the adapters.
     *
     * @return How many copies of the hints the cluster keeps
     */
    @WithDefault("1")
    int backupCount();

    /**
     * Whether a Hazelcast instance the application provides is used instead of starting
     * one of our own.
     *
     * @return Whether a Hazelcast the application provides is used instead of starting
     *         one
     */
    @WithDefault("true")
    boolean useExistingInstance();

    /**
     * How often a member which is alone in its cluster says so.
     *
     * @return How often a member which is alone in its cluster says so
     */
    @WithDefault("PT5M")
    Duration aloneReminderInterval();

    /**
     * How often a cache which cannot reach Hazelcast reports that it answers as if it were empty.
     *
     * @return How often a failing cache reports that it answers as if it were empty
     */
    @WithDefault("PT1M")
    Duration failureLogInterval();

  }

  /**
   * The overlay as the model which does not depend on a platform. Everything below the platform
   * integrations works with that model.
   *
   * @return The properties
   */
  default HazelcastElectionCacheProperties toCore() {

    final var hazelcast = workflowAdapterCache().hazelcast();

    final var properties = new HazelcastElectionCacheProperties();
    properties.setEnabled(hazelcast.enabled());
    hazelcast.clusterName().ifPresent(properties::setClusterName);
    properties.setMapName(hazelcast.mapName());
    properties
        .setDiscovery(HazelcastElectionCacheProperties.Discovery.of(hazelcast.discovery()));
    properties
        .setMembers(
            hazelcast
                .members()
                .orElseGet(List::of)
                .stream()
                .filter(member -> !member.isBlank())
                .collect(Collectors.toCollection(ArrayList::new)));
    properties.setPort(hazelcast.port());
    properties.setPortAutoIncrement(hazelcast.portAutoIncrement());
    hazelcast.multicastGroup().ifPresent(properties::setMulticastGroup);
    hazelcast.multicastPort().ifPresent(properties::setMulticastPort);
    hazelcast.kubernetesServiceDns().ifPresent(properties::setKubernetesServiceDns);
    hazelcast.kubernetesServiceName().ifPresent(properties::setKubernetesServiceName);
    hazelcast.kubernetesNamespace().ifPresent(properties::setKubernetesNamespace);
    properties.setBackupCount(hazelcast.backupCount());
    properties.setUseExistingInstance(hazelcast.useExistingInstance());
    properties.setAloneReminderInterval(hazelcast.aloneReminderInterval());
    properties.setFailureLogInterval(hazelcast.failureLogInterval());
    return properties;

  }

}
