package io.vanillabp.electioncache.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.KubernetesConfig;

/**
 * The Hazelcast configuration of a member which this repository starts. It is built from
 * {@link HazelcastElectionCacheProperties} and nothing else. No <code>hazelcast.xml</code> is
 * read. So a file which an application put on the classpath for its own Hazelcast cannot silently
 * change the cluster the election cache joins.
 * <p>
 * It is a member and not a client, because the nodes of the application ARE the cluster (see
 * decision 1 in the repository's DECISIONS.md). But it is not a Hazelcast for every purpose. Jet
 * is off, telemetry is off, and the only map configured is the one which holds the hints. An
 * application which wants more gives us its own instance instead.
 */
final class ElectionCacheMemberConfig {

  /**
   * Hazelcast sends usage data to its vendor unless this is off. A member embedded in somebody
   * else's application should not decide that for them, so it is off. An application which wants
   * it configures its own instance and hands it over.
   */
  private static final String PHONE_HOME_PROPERTY = "hazelcast.phone.home.enabled";

  private ElectionCacheMemberConfig() {

  }

  /**
   * Builds the configuration of the member.
   *
   * @param properties What the application configured
   * @param clusterName The cluster to join
   * @param classLoader The class loader Hazelcast deserializes with. It is the one which loaded
   *          this repository, so the entry processor which marks an ended workflow is found on
   *          every platform
   * @return The configuration
   */
  static Config of(
      final HazelcastElectionCacheProperties properties,
      final String clusterName,
      final ClassLoader classLoader) {

    final var config = new Config();
    config.setClusterName(clusterName);
    config.setClassLoader(classLoader);
    config.setProperty(PHONE_HOME_PROPERTY, "false");
    // the stream engine (Jet) is a second product inside the same member. It starts
    // threads and opens a port for jobs which nobody sends here
    config.getJetConfig().setEnabled(false);

    config
        .getMapConfig(properties.getMapName())
        .setBackupCount(properties.getBackupCount())
        // each hint carries its own lifetime. One lifetime for the whole map could not
        // tell a running workflow from an ended one
        .setTimeToLiveSeconds(0);

    final var network = config.getNetworkConfig();
    network.setPort(properties.getPort());
    network.setPortAutoIncrement(properties.isPortAutoIncrement());

    join(network.getJoin(), properties);

    return config;

  }

  /**
   * Turns on exactly one way of finding members. Hazelcast refuses a configuration with two of
   * them turned on. And its auto-detection stays on until something else is turned on. So all of
   * them are switched off first, and then the configured one is switched on.
   */
  private static void join(
      final JoinConfig join,
      final HazelcastElectionCacheProperties properties) {

    join.getAutoDetectionConfig().setEnabled(false);
    join.getMulticastConfig().setEnabled(false);
    join.getTcpIpConfig().setEnabled(false);

    switch (properties.getDiscovery()) {
      case AUTO_DETECT -> join.getAutoDetectionConfig().setEnabled(true);
      case MULTICAST -> {
        final var multicast = join.getMulticastConfig();
        multicast.setEnabled(true);
        if ((properties.getMulticastGroup() != null) && !properties.getMulticastGroup().isBlank()) {
          multicast.setMulticastGroup(properties.getMulticastGroup());
        }
        if (properties.getMulticastPort() != null) {
          multicast.setMulticastPort(properties.getMulticastPort());
        }
      }
      case MEMBERS -> join
          .getTcpIpConfig()
          .setEnabled(true)
          .setMembers(properties.getMembers());
      case KUBERNETES -> join.setKubernetesConfig(kubernetes(properties));
    }

  }

  /**
   * The Kubernetes discovery, in one of its two modes. The DNS mode needs neither an address nor
   * a permission. A headless service resolves to the addresses of its ready pods, and that is all
   * a starting member has to read.
   */
  private static KubernetesConfig kubernetes(
      final HazelcastElectionCacheProperties properties) {

    final var kubernetes = new KubernetesConfig();
    kubernetes.setEnabled(true);

    if (properties.isKubernetesByDns()) {
      kubernetes.setProperty("service-dns", properties.getKubernetesServiceDns());
      return kubernetes;
    }

    kubernetes.setProperty("service-name", properties.getKubernetesServiceName());
    if ((properties.getKubernetesNamespace() != null) && !properties.getKubernetesNamespace().isBlank()) {
      kubernetes.setProperty("namespace", properties.getKubernetesNamespace());
    }
    return kubernetes;

  }

}
