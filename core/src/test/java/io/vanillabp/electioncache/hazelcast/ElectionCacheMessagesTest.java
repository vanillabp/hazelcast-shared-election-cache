package io.vanillabp.electioncache.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vanillabp.electioncache.hazelcast.HazelcastElectionCacheProperties.Discovery;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * The sentences an operator reads. Each of them promises three things - what happened,
 * what it costs and what to do - and this test holds all three, because a message which
 * only reports an event sends whoever reads it into the code.
 */
@ExtendWith(SuppressOutputExtension.class)
public class ElectionCacheMessagesTest {

  @Test
  @DisplayName("The startup line names the cluster, its size and the way the members found each other")
  public void theStartupLineNamesTheCluster() {

    final var properties = new HazelcastElectionCacheProperties();

    final var message = ElectionCacheMessages
        .sharing(
            "taxi-ride-vanillabp-election-cache",
            "[127.0.0.1]:5701",
            2,
            properties.getMapName(),
            1,
            Duration.ofHours(1),
            Duration.ofMinutes(5),
            ElectionCacheMessages.discoveryDescription(properties));

    assertThat(message)
        .contains("taxi-ride-vanillabp-election-cache")
        .contains("2 member(s)")
        .contains("[127.0.0.1]:5701")
        .contains(properties.getMapName())
        .contains("PT1H")
        .contains("PT5M")
        .contains("auto-detection");

  }

  @Test
  @DisplayName("A node which is alone says that its cache is private and what that costs")
  public void theAloneWarningSaysWhatItCosts() {

    final var message = ElectionCacheMessages.alone("elections", new HazelcastElectionCacheProperties());

    assertThat(message)
        .contains("this node alone")
        .contains("PRIVATE")
        .contains("Nothing fails")
        .contains("probes the BPMS adapters again");

  }

  @Test
  @DisplayName("What to check names the property and what outside the application blocks it")
  public void whatToCheckFitsTheWayMembersAreLookedFor() {

    final var autoDetect = new HazelcastElectionCacheProperties();
    assertThat(ElectionCacheMessages.whatToCheck(autoDetect))
        .contains(HazelcastElectionCacheProperties.DISCOVERY_PROPERTY)
        .contains(HazelcastElectionCacheProperties.MEMBERS_PROPERTY)
        .contains(HazelcastElectionCacheProperties.KUBERNETES_SERVICE_DNS_PROPERTY);

    final var multicast = new HazelcastElectionCacheProperties();
    multicast.setDiscovery(Discovery.MULTICAST);
    assertThat(ElectionCacheMessages.whatToCheck(multicast))
        .contains("filter")
        .contains(HazelcastElectionCacheProperties.DISCOVERY_PROPERTY);

    final var members = new HazelcastElectionCacheProperties();
    members.setDiscovery(Discovery.MEMBERS);
    members.setMembers(List.of("10.0.0.1:5701"));
    assertThat(ElectionCacheMessages.whatToCheck(members))
        .contains("10.0.0.1:5701")
        .contains("port 5701")
        .contains("hands over the full membership");

    final var byDns = new HazelcastElectionCacheProperties();
    byDns.setDiscovery(Discovery.KUBERNETES);
    byDns.setKubernetesServiceDns("taxi-ride.default.svc.cluster.local");
    assertThat(ElectionCacheMessages.whatToCheck(byDns))
        .contains("taxi-ride.default.svc.cluster.local")
        .contains("HEADLESS")
        .contains("kubectl get endpoints");

    final var byApi = new HazelcastElectionCacheProperties();
    byApi.setDiscovery(Discovery.KUBERNETES);
    byApi.setKubernetesServiceName("taxi-ride");
    byApi.setKubernetesNamespace("rides");
    assertThat(ElectionCacheMessages.whatToCheck(byApi))
        .contains("'taxi-ride'")
        .contains("namespace 'rides'")
        .contains("role which allows this pod to read endpoints")
        .contains(HazelcastElectionCacheProperties.KUBERNETES_SERVICE_DNS_PROPERTY);

    // the namespace is left out where the pod's own is meant
    byApi.setKubernetesNamespace(null);
    assertThat(ElectionCacheMessages.whatToCheck(byApi)).doesNotContain("namespace '");

  }

  @Test
  @DisplayName("Every way of finding members is described for the startup line")
  public void everyDiscoveryIsDescribed() {

    for (final var discovery : Discovery.values()) {
      final var properties = new HazelcastElectionCacheProperties();
      properties.setDiscovery(discovery);
      properties.setMembers(List.of("10.0.0.1"));
      properties.setKubernetesServiceName("taxi-ride");

      assertThat(ElectionCacheMessages.discoveryDescription(properties)).isNotBlank();
    }

    final var byDns = new HazelcastElectionCacheProperties();
    byDns.setDiscovery(Discovery.KUBERNETES);
    byDns.setKubernetesServiceDns("taxi-ride.default.svc.cluster.local");
    assertThat(ElectionCacheMessages.discoveryDescription(byDns)).contains("headless");

  }

  @Test
  @DisplayName("A membership change is reported with the size after it, and a departure with its cost")
  public void membershipChangesAreReportedWithTheSize() {

    assertThat(ElectionCacheMessages.memberJoined("[127.0.0.1]:5702", "elections", 2))
        .contains("[127.0.0.1]:5702")
        .contains("2 member(s)");

    assertThat(ElectionCacheMessages.memberLeft("[127.0.0.1]:5702", "elections", 1))
        .contains("1 member(s)")
        .contains("one probing walk");

  }

  @Test
  @DisplayName("A failing cache says what it answers, that nothing fails and how often this is reported")
  public void aFailureSaysWhatItCosts() {

    final var first = ElectionCacheMessages.cacheFailed("read the hint of a workflow", 0, Duration.ofMinutes(1));

    assertThat(first)
        .contains("read the hint of a workflow")
        .contains("as if it were empty")
        .contains("unaffected")
        .contains("PT1M")
        .doesNotContain("further failure");

    assertThat(ElectionCacheMessages.cacheFailed("store the hint of a workflow", 17, Duration.ofMinutes(1)))
        .contains("17 further failure(s)");

  }

  @Test
  @DisplayName("A member which could not start names the fallback and its cost")
  public void aFailedStartNamesTheFallback() {

    assertThat(ElectionCacheMessages.startFailed(new HazelcastElectionCacheProperties()))
        .contains("in-memory election cache")
        .contains("not shared")
        .contains("no operation fails")
        .contains(HazelcastElectionCacheProperties.DISCOVERY_PROPERTY);

  }

  @Test
  @DisplayName("Moving into the application's own Hazelcast says what is then not read")
  public void theApplicationsInstanceIsAnnounced() {

    assertThat(ElectionCacheMessages.usingTheApplicationsInstance("vanillabp-election-cache", "the-applications"))
        .contains("vanillabp-election-cache")
        .contains("the-applications")
        .contains(HazelcastElectionCacheProperties.USE_EXISTING_INSTANCE_PROPERTY)
        .contains("lifetimes of the hints are ours");

  }

}
