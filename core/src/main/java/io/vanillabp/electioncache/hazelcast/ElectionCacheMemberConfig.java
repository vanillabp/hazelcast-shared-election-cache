package io.vanillabp.electioncache.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.KubernetesConfig;

/**
 * The Hazelcast configuration of a member started by this repository, built from
 * {@link HazelcastElectionCacheProperties} and from nothing else: no
 * <code>hazelcast.xml</code> is read, so a file an application put on the classpath for
 * its own Hazelcast cannot silently change the cluster the election cache joins.
 * <p>
 * The member is a member and not a client, because the nodes of the application ARE the
 * cluster (see decision 1 in the repository's DECISIONS.md). What it is not is a
 * general-purpose Hazelcast: Jet is off, telemetry is off, and the only map configured
 * is the one holding the hints. An application which wants more than that gives us its
 * own instance instead.
 */
final class ElectionCacheMemberConfig {

  /**
   * Hazelcast reports usage data to its vendor unless this is off. An embedded member
   * inside somebody's application is not the place to decide that for them, so it is
   * off; an application which wants it configures its own instance and hands it over.
   */
  private static final String PHONE_HOME_PROPERTY = "hazelcast.phone.home.enabled";

  private ElectionCacheMemberConfig() {

  }

  /**
   * Builds the configuration of the member.
   *
   * @param properties What the application configured
   * @param clusterName The cluster to join
   * @param classLoader The class loader Hazelcast deserializes with - the one this
   *          repository was loaded by, so the entry processor marking an ended workflow
   *          is found on every platform
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
    // the stream engine is a second product living in the same member: it starts
    // threads and opens a port for jobs nobody submits here
    config.getJetConfig().setEnabled(false);

    config
        .getMapConfig(properties.getMapName())
        .setBackupCount(properties.getBackupCount())
        // a hint carries its own lifetime, per entry: a map-wide one could not tell a
        // living workflow from an ended one
        .setTimeToLiveSeconds(0);

    final var network = config.getNetworkConfig();
    network.setPort(properties.getPort());
    network.setPortAutoIncrement(properties.isPortAutoIncrement());

    join(network.getJoin(), properties);

    return config;

  }

  /**
   * Enables exactly one way of finding members. Hazelcast refuses a configuration with
   * two of them enabled, and its auto-detection is on until something else is, which is
   * why every branch switches the others off rather than only its own on.
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
   * The Kubernetes discovery, in one of its two modes. The DNS mode is the one which
   * needs neither an address nor a permission: a headless service resolves to the
   * addresses of its ready pods, and that is all a starting member has to read.
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
