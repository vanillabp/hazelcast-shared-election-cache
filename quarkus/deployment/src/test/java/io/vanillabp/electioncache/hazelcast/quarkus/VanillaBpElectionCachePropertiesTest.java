package io.vanillabp.electioncache.hazelcast.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.vanillabp.electioncache.hazelcast.HazelcastElectionCacheProperties;
import io.vanillabp.electioncache.hazelcast.HazelcastElectionCacheProperties.Discovery;
import io.vanillabp.electioncache.hazelcast.quarkus.runtime.VanillaBpElectionCacheProperties;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * The overlay of the shared <code>vanillabp.*</code> tree, read without booting an
 * application. Two things are worth this test: the defaults are written a second time in
 * the mapping, because a mapping cannot read a constant, and a default which differs
 * between the platforms would be a bug nobody notices for months; and the way of finding
 * members arrives here as the string it was written as.
 */
@ExtendWith(SuppressOutputExtension.class)
public class VanillaBpElectionCachePropertiesTest {

  @Test
  @DisplayName("An unconfigured application reads exactly the defaults of the core model")
  public void theDefaultsAreTheOnesOfTheCoreModel() {

    final var mapped = read(Map.of());
    final var core = new HazelcastElectionCacheProperties();

    assertThat(mapped.isEnabled()).isEqualTo(core.isEnabled());
    assertThat(mapped.getClusterName()).isEqualTo(core.getClusterName());
    assertThat(mapped.getMapName()).isEqualTo(core.getMapName());
    assertThat(mapped.getDiscovery()).isEqualTo(core.getDiscovery());
    assertThat(mapped.getMembers()).isEqualTo(core.getMembers());
    assertThat(mapped.getPort()).isEqualTo(core.getPort());
    assertThat(mapped.isPortAutoIncrement()).isEqualTo(core.isPortAutoIncrement());
    assertThat(mapped.getMulticastGroup()).isEqualTo(core.getMulticastGroup());
    assertThat(mapped.getMulticastPort()).isEqualTo(core.getMulticastPort());
    assertThat(mapped.getKubernetesServiceDns()).isEqualTo(core.getKubernetesServiceDns());
    assertThat(mapped.getKubernetesServiceName()).isEqualTo(core.getKubernetesServiceName());
    assertThat(mapped.getKubernetesNamespace()).isEqualTo(core.getKubernetesNamespace());
    assertThat(mapped.getBackupCount()).isEqualTo(core.getBackupCount());
    assertThat(mapped.isUseExistingInstance()).isEqualTo(core.isUseExistingInstance());
    assertThat(mapped.getAloneReminderInterval()).isEqualTo(core.getAloneReminderInterval());
    assertThat(mapped.getFailureLogInterval()).isEqualTo(core.getFailureLogInterval());

  }

  @Test
  @DisplayName("Every key arrives in the core model, hyphenated values included")
  public void everyKeyArrives() {

    final var mapped = read(
        Map
            .ofEntries(
                Map.entry(HazelcastElectionCacheProperties.ENABLED_PROPERTY, "false"),
                Map.entry(HazelcastElectionCacheProperties.CLUSTER_NAME_PROPERTY, "elections"),
                Map.entry(HazelcastElectionCacheProperties.MAP_NAME_PROPERTY, "hints"),
                Map.entry(HazelcastElectionCacheProperties.DISCOVERY_PROPERTY, "kubernetes"),
                Map.entry(HazelcastElectionCacheProperties.PORT_PROPERTY, "5799"),
                Map.entry(HazelcastElectionCacheProperties.PORT_AUTO_INCREMENT_PROPERTY, "false"),
                Map.entry(HazelcastElectionCacheProperties.MULTICAST_GROUP_PROPERTY, "224.2.2.9"),
                Map.entry(HazelcastElectionCacheProperties.MULTICAST_PORT_PROPERTY, "54399"),
                Map
                    .entry(
                        HazelcastElectionCacheProperties.KUBERNETES_SERVICE_DNS_PROPERTY,
                        "taxi-ride.rides.svc.cluster.local"),
                Map.entry(HazelcastElectionCacheProperties.KUBERNETES_SERVICE_NAME_PROPERTY, "taxi-ride"),
                Map.entry(HazelcastElectionCacheProperties.KUBERNETES_NAMESPACE_PROPERTY, "rides"),
                Map.entry(HazelcastElectionCacheProperties.BACKUP_COUNT_PROPERTY, "0"),
                Map.entry(HazelcastElectionCacheProperties.USE_EXISTING_INSTANCE_PROPERTY, "false"),
                Map.entry(HazelcastElectionCacheProperties.ALONE_REMINDER_INTERVAL_PROPERTY, "PT30S"),
                Map.entry(HazelcastElectionCacheProperties.FAILURE_LOG_INTERVAL_PROPERTY, "PT10S")));

    assertThat(mapped.isEnabled()).isFalse();
    assertThat(mapped.getClusterName()).isEqualTo("elections");
    assertThat(mapped.getMapName()).isEqualTo("hints");
    assertThat(mapped.getDiscovery()).isEqualTo(Discovery.KUBERNETES);
    assertThat(mapped.getPort()).isEqualTo(5799);
    assertThat(mapped.isPortAutoIncrement()).isFalse();
    assertThat(mapped.getMulticastGroup()).isEqualTo("224.2.2.9");
    assertThat(mapped.getMulticastPort()).isEqualTo(54399);
    assertThat(mapped.getKubernetesServiceDns()).isEqualTo("taxi-ride.rides.svc.cluster.local");
    assertThat(mapped.getKubernetesServiceName()).isEqualTo("taxi-ride");
    assertThat(mapped.getKubernetesNamespace()).isEqualTo("rides");
    assertThat(mapped.getBackupCount()).isZero();
    assertThat(mapped.isUseExistingInstance()).isFalse();
    assertThat(mapped.getAloneReminderInterval()).isEqualTo(Duration.ofSeconds(30));
    assertThat(mapped.getFailureLogInterval()).isEqualTo(Duration.ofSeconds(10));

  }

  @Test
  @DisplayName("A list of members arrives as a list, and an empty entry is not an address")
  public void theListOfMembersArrives() {

    final var mapped = read(
        Map
            .of(
                HazelcastElectionCacheProperties.DISCOVERY_PROPERTY,
                "members",
                HazelcastElectionCacheProperties.MEMBERS_PROPERTY,
                "10.0.0.1,,10.0.0.2:5702"));

    assertThat(mapped.getDiscovery()).isEqualTo(Discovery.MEMBERS);
    assertThat(mapped.getMembers()).containsExactly("10.0.0.1", "10.0.0.2:5702");
    // and what arrives is valid, which is what the producer relies on
    mapped.validate();

  }

  private static HazelcastElectionCacheProperties read(
      final Map<String, String> properties) {

    return new SmallRyeConfigBuilder()
        .withMapping(VanillaBpElectionCacheProperties.class)
        .withSources(new PropertiesConfigSource(properties, "the application", 100))
        .build()
        .getConfigMapping(VanillaBpElectionCacheProperties.class)
        .toCore();

  }

}
