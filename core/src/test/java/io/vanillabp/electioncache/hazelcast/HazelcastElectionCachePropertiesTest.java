package io.vanillabp.electioncache.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vanillabp.electioncache.hazelcast.HazelcastElectionCacheProperties.Discovery;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * The configuration as a developer meets it: what boots with nothing configured, what
 * ends a boot, and what is only worth a warning. Every message asserted here is asserted
 * for its property key, because the key is what the reader has to act on.
 */
@ExtendWith(SuppressOutputExtension.class)
public class HazelcastElectionCachePropertiesTest {

  @Test
  @DisplayName("Nothing configured is a valid configuration")
  public void theDefaultsAreValid() {

    final var properties = new HazelcastElectionCacheProperties();

    properties.validate();

    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.getMapName()).isEqualTo("vanillabp-election-cache");
    assertThat(properties.getDiscovery()).isEqualTo(Discovery.AUTO_DETECT);
    assertThat(properties.getMembers()).isEmpty();
    assertThat(properties.getBackupCount()).isOne();
    assertThat(properties.isUseExistingInstance()).isTrue();

  }

  @Test
  @DisplayName("The cluster name is derived from the application's own name")
  public void theClusterNameIsDerived() {

    final var properties = new HazelcastElectionCacheProperties();

    assertThat(properties.clusterNameOrDerived("taxi-ride"))
        .isEqualTo("taxi-ride-vanillabp-election-cache");

    properties.setClusterName("elections");
    assertThat(properties.clusterNameOrDerived("taxi-ride")).isEqualTo("elections");

  }

  @Test
  @DisplayName("An application without a name gets a default cluster and a warning naming the property")
  public void anApplicationWithoutANameIsWarned() {

    final var properties = new HazelcastElectionCacheProperties();

    assertThat(properties.clusterNameOrDerived(null)).isEqualTo("vanillabp-election-cache");
    assertThat(properties.warnings(null))
        .hasSize(1)
        .first(org.assertj.core.api.InstanceOfAssertFactories.STRING)
        .contains(HazelcastElectionCacheProperties.CLUSTER_NAME_PROPERTY)
        .contains("join each other");

    // an application which says who it is needs no warning
    assertThat(properties.warnings("taxi-ride")).isEmpty();

  }

  @Test
  @DisplayName("A list of members nobody reads is a warning, not a failure")
  public void anUnreadListOfMembersIsAWarning() {

    final var properties = new HazelcastElectionCacheProperties();
    properties.setMembers(List.of("10.0.0.1"));

    properties.validate();

    assertThat(properties.warnings("taxi-ride"))
        .hasSize(1)
        .first(org.assertj.core.api.InstanceOfAssertFactories.STRING)
        .contains(HazelcastElectionCacheProperties.MEMBERS_PROPERTY)
        .contains(HazelcastElectionCacheProperties.DISCOVERY_PROPERTY);

  }

  @Test
  @DisplayName("Two Kubernetes modes at once are a warning saying which one wins")
  public void bothKubernetesModesAreAWarning() {

    final var properties = kubernetes();
    properties.setKubernetesServiceName("taxi-ride");

    properties.validate();

    assertThat(properties.warnings("taxi-ride"))
        .hasSize(1)
        .first(org.assertj.core.api.InstanceOfAssertFactories.STRING)
        .contains("DNS record wins");

  }

  @Test
  @DisplayName("An empty map name ends the boot naming the property")
  public void anEmptyMapNameEndsTheBoot() {

    final var properties = new HazelcastElectionCacheProperties();
    properties.setMapName("  ");

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(HazelcastElectionCacheProperties.MAP_NAME_PROPERTY)
        .hasMessageContaining("vanillabp-election-cache");

  }

  @Test
  @DisplayName("'members' without an address ends the boot and names the way out")
  public void membersWithoutAnAddressEndsTheBoot() {

    final var properties = new HazelcastElectionCacheProperties();
    properties.setDiscovery(Discovery.MEMBERS);

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(HazelcastElectionCacheProperties.MEMBERS_PROPERTY)
        // the sentence which keeps somebody from listing every node of the cluster
        .hasMessageContaining("not every node has to be listed")
        .hasMessageContaining(HazelcastElectionCacheProperties.KUBERNETES_SERVICE_DNS_PROPERTY);

  }

  @Test
  @DisplayName("'kubernetes' without a service ends the boot, DNS first")
  public void kubernetesWithoutAServiceEndsTheBoot() {

    final var properties = new HazelcastElectionCacheProperties();
    properties.setDiscovery(Discovery.KUBERNETES);

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(HazelcastElectionCacheProperties.KUBERNETES_SERVICE_DNS_PROPERTY)
        .hasMessageContaining(HazelcastElectionCacheProperties.KUBERNETES_SERVICE_NAME_PROPERTY)
        .hasMessageContaining("no permission on the Kubernetes API");

  }

  @Test
  @DisplayName("A port outside the range, a backup count outside it, and an interval which is not one")
  public void theNumbersAreChecked() {

    final var withoutAPort = new HazelcastElectionCacheProperties();
    withoutAPort.setPort(70000);
    assertThatThrownBy(withoutAPort::validate)
        .hasMessageContaining(HazelcastElectionCacheProperties.PORT_PROPERTY)
        .hasMessageContaining("not the application's HTTP port");

    final var withABadMulticastPort = new HazelcastElectionCacheProperties();
    withABadMulticastPort.setMulticastPort(0);
    assertThatThrownBy(withABadMulticastPort::validate)
        .hasMessageContaining(HazelcastElectionCacheProperties.MULTICAST_PORT_PROPERTY);

    final var withTooManyBackups = new HazelcastElectionCacheProperties();
    withTooManyBackups.setBackupCount(7);
    assertThatThrownBy(withTooManyBackups::validate)
        .hasMessageContaining(HazelcastElectionCacheProperties.BACKUP_COUNT_PROPERTY)
        .hasMessageContaining("probing walk");

    final var withoutAReminder = new HazelcastElectionCacheProperties();
    withoutAReminder.setAloneReminderInterval(Duration.ZERO);
    assertThatThrownBy(withoutAReminder::validate)
        .hasMessageContaining(HazelcastElectionCacheProperties.ALONE_REMINDER_INTERVAL_PROPERTY);

    final var withoutAFailureInterval = new HazelcastElectionCacheProperties();
    withoutAFailureInterval.setFailureLogInterval(null);
    assertThatThrownBy(withoutAFailureInterval::validate)
        .hasMessageContaining(HazelcastElectionCacheProperties.FAILURE_LOG_INTERVAL_PROPERTY);

    final var withoutADiscovery = new HazelcastElectionCacheProperties();
    withoutADiscovery.setDiscovery(null);
    assertThatThrownBy(withoutADiscovery::validate)
        .hasMessageContaining(HazelcastElectionCacheProperties.DISCOVERY_PROPERTY)
        .hasMessageContaining("auto-detect");

  }

  @Test
  @DisplayName("A way of finding members is read hyphenated, and a typo names the four which exist")
  public void discoveryIsReadHyphenated() {

    assertThat(Discovery.of("auto-detect")).isEqualTo(Discovery.AUTO_DETECT);
    assertThat(Discovery.of("AUTO_DETECT")).isEqualTo(Discovery.AUTO_DETECT);
    assertThat(Discovery.of(" kubernetes ")).isEqualTo(Discovery.KUBERNETES);
    assertThat(Discovery.MEMBERS.key()).isEqualTo("members");
    assertThat(Discovery.AUTO_DETECT.key()).isEqualTo("auto-detect");

    assertThatThrownBy(() -> Discovery.of("k8s"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(HazelcastElectionCacheProperties.DISCOVERY_PROPERTY)
        .hasMessageContaining("'k8s'")
        .hasMessageContaining("'kubernetes'");

    assertThatThrownBy(() -> Discovery.of(null))
        .isInstanceOf(IllegalStateException.class);

  }

  @Test
  @DisplayName("The Kubernetes mode is decided by which of the two is set")
  public void theKubernetesModeIsDecidedByTheConfiguration() {

    assertThat(kubernetes().isKubernetesByDns()).isTrue();

    final var byApi = new HazelcastElectionCacheProperties();
    byApi.setDiscovery(Discovery.KUBERNETES);
    byApi.setKubernetesServiceName("taxi-ride");
    byApi.validate();

    assertThat(byApi.isKubernetesByDns()).isFalse();

  }

  private static HazelcastElectionCacheProperties kubernetes() {

    final var properties = new HazelcastElectionCacheProperties();
    properties.setDiscovery(Discovery.KUBERNETES);
    properties.setKubernetesServiceDns("taxi-ride.default.svc.cluster.local");
    return properties;

  }

}
