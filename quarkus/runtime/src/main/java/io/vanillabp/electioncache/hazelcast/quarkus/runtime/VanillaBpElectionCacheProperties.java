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
 * This cache's OVERLAY of the shared <code>vanillabp.*</code> configuration tree: the
 * keys under <code>vanillabp.workflow-adapter-cache.hazelcast</code>, inside the section
 * the platform owns, because a team raising the lifetime of a hint and a team pointing
 * the members at a Kubernetes service are configuring the same cache.
 * <p>
 * Quarkus knows no blanket ignore for the <code>vanillabp</code> prefix, so a key no
 * registered mapping models fails the startup. This overlay is therefore what makes
 * these keys writable at all.
 * <p>
 * Never {@code @Inject} this mapping: injecting it turns it into a STATIC-INIT mapping
 * and the whole tree is validated before the extensions registered their RUN_TIME
 * overlays. Read it through
 * {@code ConfigProvider.getConfig().unwrap(SmallRyeConfig.class).getConfigMapping(...)}
 * instead, which is what {@link HazelcastElectionCacheProducer} does.
 * <p>
 * The defaults are the ones of {@link HazelcastElectionCacheProperties} and are written
 * twice on purpose: a mapping cannot read a constant, and a default which differs
 * between the platforms would be worse than a duplicated literal. The tests of both
 * platforms compare them.
 */
@StaticInitSafe
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "vanillabp")
public interface VanillaBpElectionCacheProperties {

  /**
   * The platform's election-cache section, of which only this cache's own subsection is
   * modeled here.
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
     * Whether the election cache runs on Hazelcast at all. Switching it off leaves the
     * hints in the in-memory cache of the platform, which every node keeps for itself.
     *
     * @return Whether the election cache runs on Hazelcast at all
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * The name of the Hazelcast cluster the nodes of this application form. Derived from
     * the application's own name where unset, which is what keeps two applications on one
     * network apart.
     *
     * @return The name of the Hazelcast cluster, derived from the application's name
     *         where unset
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
     * The addresses a starting member tries, as 'host' or 'host:port'. They are seeds and
     * not an inventory: the member which answers hands over the full membership, so not
     * every node has to be listed.
     *
     * @return The seed addresses a starting member tries, not the full membership
     */
    Optional<List<String>> members();

    /**
     * The port the members talk to each other on. Not the application's HTTP port, and it
     * has to be open between the nodes.
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
     * The multicast group, Hazelcast's own default where unset.
     *
     * @return The multicast group, Hazelcast's own default where unset
     */
    Optional<String> multicastGroup();

    /**
     * The multicast port, Hazelcast's own default where unset.
     *
     * @return The multicast port, Hazelcast's own default where unset
     */
    Optional<Integer> multicastPort();

    /**
     * The DNS name of a HEADLESS Kubernetes service in front of the pods. This is the way
     * which needs neither multicast nor any address nor a permission on the Kubernetes
     * API.
     *
     * @return The DNS name of a headless Kubernetes service, the way which needs no
     *         address and no permission
     */
    Optional<String> kubernetesServiceDns();

    /**
     * The name of a Kubernetes service whose endpoints are read through the Kubernetes
     * API, which needs a role allowing the pod to do so.
     *
     * @return The name of a Kubernetes service whose endpoints are read through the
     *         Kubernetes API
     */
    Optional<String> kubernetesServiceName();

    /**
     * The Kubernetes namespace of that service, the pod's own where unset.
     *
     * @return The namespace of that service, the pod's own where unset
     */
    Optional<String> kubernetesNamespace();

    /**
     * How many copies of each partition the cluster keeps. A cost decision and not a
     * correctness one: a hint which is gone costs one probing walk.
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
     * How often a cache which cannot reach Hazelcast reports that it is answering as if it
     * were empty.
     *
     * @return How often a failing cache reports that it answers as if it were empty
     */
    @WithDefault("PT1M")
    Duration failureLogInterval();

  }

  /**
   * The overlay as the platform-neutral model, which is what everything below the
   * platform integrations works with.
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
