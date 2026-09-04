package io.vanillabp.electioncache.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vanillabp.electioncache.hazelcast.HazelcastElectionCacheProperties.Discovery;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * What each way of finding members turns into. Hazelcast refuses a configuration with
 * two of them enabled and has its auto-detection on until something else is, so every
 * branch has to switch the others off - which is the kind of thing that is right once
 * and wrong after the next addition.
 */
@ExtendWith(SuppressOutputExtension.class)
public class ElectionCacheMemberConfigTest {

  @Test
  @DisplayName("Auto-detection is the only one enabled by default")
  public void autoDetectionIsTheDefault() {

    final var join = config(new HazelcastElectionCacheProperties()).getNetworkConfig().getJoin();

    assertThat(join.getAutoDetectionConfig().isEnabled()).isTrue();
    assertThat(join.getMulticastConfig().isEnabled()).isFalse();
    assertThat(join.getTcpIpConfig().isEnabled()).isFalse();
    assertThat(join.getKubernetesConfig().isEnabled()).isFalse();

  }

  @Test
  @DisplayName("Multicast takes the group and the port where they are configured")
  public void multicastTakesGroupAndPort() {

    final var properties = new HazelcastElectionCacheProperties();
    properties.setDiscovery(Discovery.MULTICAST);
    properties.setMulticastGroup("224.2.2.9");
    properties.setMulticastPort(54399);

    final var join = config(properties).getNetworkConfig().getJoin();

    assertThat(join.getAutoDetectionConfig().isEnabled()).isFalse();
    assertThat(join.getMulticastConfig().isEnabled()).isTrue();
    assertThat(join.getMulticastConfig().getMulticastGroup()).isEqualTo("224.2.2.9");
    assertThat(join.getMulticastConfig().getMulticastPort()).isEqualTo(54399);

    // and Hazelcast's own defaults stay where nothing is configured
    final var plain = new HazelcastElectionCacheProperties();
    plain.setDiscovery(Discovery.MULTICAST);
    final var plainMulticast = config(plain).getNetworkConfig().getJoin().getMulticastConfig();
    assertThat(plainMulticast.getMulticastGroup()).isNotBlank();
    assertThat(plainMulticast.getMulticastPort()).isPositive();

  }

  @Test
  @DisplayName("A list of members becomes the addresses a starting member tries")
  public void membersBecomeTheSeeds() {

    final var properties = new HazelcastElectionCacheProperties();
    properties.setDiscovery(Discovery.MEMBERS);
    properties.setMembers(List.of("10.0.0.1", "10.0.0.2:5702"));

    final var join = config(properties).getNetworkConfig().getJoin();

    assertThat(join.getTcpIpConfig().isEnabled()).isTrue();
    assertThat(join.getTcpIpConfig().getMembers()).containsExactly("10.0.0.1", "10.0.0.2:5702");
    assertThat(join.getAutoDetectionConfig().isEnabled()).isFalse();

  }

  @Test
  @DisplayName("Kubernetes by DNS carries the service DNS name and nothing else")
  public void kubernetesByDns() {

    final var properties = new HazelcastElectionCacheProperties();
    properties.setDiscovery(Discovery.KUBERNETES);
    properties.setKubernetesServiceDns("taxi-ride.rides.svc.cluster.local");
    properties.setKubernetesNamespace("ignored-in-this-mode");

    final var kubernetes = config(properties).getNetworkConfig().getJoin().getKubernetesConfig();

    assertThat(kubernetes.isEnabled()).isTrue();
    assertThat(kubernetes.getProperties())
        .containsExactly(java.util.Map.entry("service-dns", "taxi-ride.rides.svc.cluster.local"));

  }

  @Test
  @DisplayName("Kubernetes by API carries the service and, where set, the namespace")
  public void kubernetesByApi() {

    final var properties = new HazelcastElectionCacheProperties();
    properties.setDiscovery(Discovery.KUBERNETES);
    properties.setKubernetesServiceName("taxi-ride");

    assertThat(config(properties).getNetworkConfig().getJoin().getKubernetesConfig().getProperties())
        .containsExactly(java.util.Map.entry("service-name", "taxi-ride"));

    properties.setKubernetesNamespace("rides");

    assertThat(config(properties).getNetworkConfig().getJoin().getKubernetesConfig().getProperties())
        .containsOnly(
            java.util.Map.entry("service-name", "taxi-ride"),
            java.util.Map.entry("namespace", "rides"));

  }

  @Test
  @DisplayName("The member is a cache and not a Hazelcast installation")
  public void theMemberCarriesNothingItDoesNotNeed() {

    final var properties = new HazelcastElectionCacheProperties();
    properties.setBackupCount(0);
    properties.setPort(5799);
    properties.setPortAutoIncrement(false);

    final var config = config(properties);

    assertThat(config.getClusterName()).isEqualTo("elections");
    assertThat(config.getJetConfig().isEnabled()).isFalse();
    assertThat(config.getProperty("hazelcast.phone.home.enabled")).isEqualTo("false");
    assertThat(config.getNetworkConfig().getPort()).isEqualTo(5799);
    assertThat(config.getNetworkConfig().isPortAutoIncrement()).isFalse();

    final var map = config.getMapConfig(properties.getMapName());
    assertThat(map.getBackupCount()).isZero();
    // the lifetime travels with each entry: a map-wide one could not tell a living
    // workflow from an ended one
    assertThat(map.getTimeToLiveSeconds()).isZero();

  }

  private static com.hazelcast.config.Config config(
      final HazelcastElectionCacheProperties properties) {

    return ElectionCacheMemberConfig
        .of(properties, "elections", ElectionCacheMemberConfigTest.class.getClassLoader());

  }

}
