package io.vanillabp.electioncache.hazelcast;

import java.time.Duration;

/**
 * What this cache logs at startup, while it runs and when it fails. The sentences are kept here
 * for two reasons. Both platform integrations log the same ones. And each of them is meant to end
 * the search of an operator, not just to record an event. That is easier to keep true in one
 * place than in several.
 * <p>
 * Each message says what happened, what it costs and what to do, in that order. The cost is never
 * nothing, and it is never an outage. A cache of hints which is gone costs one round of asking the
 * adapters per operation. Saying so keeps somebody from treating a cluster which did not form as
 * an incident. It also keeps them from taking a shared cache which is private to each node for a
 * working one.
 */
public final class ElectionCacheMessages {

  private ElectionCacheMessages() {

  }

  /**
   * The line a working cache logs at startup. It names the cluster, its size and the way the
   * members found each other. Without this line, an operator would need a debugger for these
   * three.
   *
   * @param clusterName The Hazelcast cluster the member joined
   * @param localAddress The address this member listens on
   * @param memberCount How many members the cluster has, this one included
   * @param mapName The name of the map holding the hints
   * @param backupCount How many copies of a partition the cluster keeps
   * @param timeToLive How long the hint of a running workflow is kept
   * @param endedTimeToLive How long the hint of an ended workflow is kept
   * @param discovery What the members find each other by
   * @return The message
   */
  public static String sharing(
      final String clusterName,
      final String localAddress,
      final int memberCount,
      final String mapName,
      final int backupCount,
      final Duration timeToLive,
      final Duration endedTimeToLive,
      final String discovery) {

    return """
        VanillaBP shares its BPMS elections through Hazelcast: cluster '%s' has %d member(s), this \
        one listens on %s, the hints live in map '%s' with %d backup(s) for %s (a workflow which \
        ended: %s). The members find each other by %s."""
        .formatted(
            clusterName,
            memberCount,
            localAddress,
            mapName,
            backupCount,
            timeToLive,
            endedTimeToLive,
            discovery);

  }

  /**
   * The warning which a member that found nobody repeats for as long as it is alone. This is the
   * trap of an embedded cluster. Nothing fails, and the application behaves exactly as with the
   * in-memory default. So the reason why somebody added a shared cache is still there.
   *
   * @param clusterName The cluster this member is alone in
   * @param properties The configuration. It picks the hint which fits the way members are
   *          looked for
   * @return The message
   */
  public static String alone(
      final String clusterName,
      final HazelcastElectionCacheProperties properties) {

    return """
        VanillaBP found no other Hazelcast member: cluster '%s' consists of this node alone, so its \
        election cache is PRIVATE to this node and does exactly what the in-memory default does - \
        which is the situation a shared cache is added to leave behind. Nothing fails because of \
        it: every node just probes the BPMS adapters again for a workflow another node already \
        elected. %s"""
        .formatted(clusterName, whatToCheck(properties));

  }

  /**
   * What to check when a cluster does not form, for each way of finding members. This half of the
   * warning saves the search. So it names the property, and the thing outside the application
   * which usually blocks it.
   *
   * @param properties The configuration
   * @return The hint
   */
  public static String whatToCheck(
      final HazelcastElectionCacheProperties properties) {

    return switch (properties.getDiscovery()) {
      case AUTO_DETECT -> """
          Members are looked for by Hazelcast's auto-detection, which tries multicast and its cloud \
          plugins - it finds nobody in most container networks. Set '%s' to 'members' and name a \
          node or two under '%s', or to 'kubernetes' and name a headless service under '%s'."""
          .formatted(
              HazelcastElectionCacheProperties.DISCOVERY_PROPERTY,
              HazelcastElectionCacheProperties.MEMBERS_PROPERTY,
              HazelcastElectionCacheProperties.KUBERNETES_SERVICE_DNS_PROPERTY);
      case MULTICAST -> """
          Members are looked for by multicast, which most container networks and every cloud filter. \
          Check that it is allowed between the nodes, or set '%s' to 'members' respectively \
          'kubernetes'."""
          .formatted(HazelcastElectionCacheProperties.DISCOVERY_PROPERTY);
      case MEMBERS -> """
          Members are looked for at %s, and port %d has to be open between the nodes - it is not the \
          application's HTTP port. Not every node has to be listed under '%s': one reachable member \
          hands over the full membership, so an unreachable list is the thing to check, not an \
          incomplete one."""
          .formatted(
              properties.getMembers(),
              properties.getPort(),
              HazelcastElectionCacheProperties.MEMBERS_PROPERTY);
      case KUBERNETES -> properties.isKubernetesByDns()
          ? """
              Members are looked for at the DNS record '%s', which has to belong to a HEADLESS \
              service (clusterIP: None) in front of these pods - a service with a cluster IP \
              resolves to itself and a member then finds only itself. Check what it resolves to \
              ('kubectl get endpoints') and that port %d is allowed between the pods."""
              .formatted(properties.getKubernetesServiceDns(), properties.getPort())
          : """
              Members are looked for through the Kubernetes API, at the endpoints of service '%s'%s. \
              That needs a role which allows this pod to read endpoints, and a pod without it finds \
              nobody. Where no such role can be granted, name a headless service under '%s' instead \
              - DNS needs no permission at all."""
              .formatted(
                  properties.getKubernetesServiceName(),
                  (properties.getKubernetesNamespace() == null) || properties.getKubernetesNamespace().isBlank()
                      ? ""
                      : " in namespace '%s'".formatted(properties.getKubernetesNamespace()),
                  HazelcastElectionCacheProperties.KUBERNETES_SERVICE_DNS_PROPERTY);
    };

  }

  /**
   * How the members of this cluster look for each other, in the words of the startup line.
   *
   * @param properties The configuration
   * @return The description
   */
  public static String discoveryDescription(
      final HazelcastElectionCacheProperties properties) {

    return switch (properties.getDiscovery()) {
      case AUTO_DETECT -> "Hazelcast's auto-detection (multicast and the cloud plugins in turn)";
      case MULTICAST -> "multicast";
      case MEMBERS -> "the addresses %s, which are seeds rather than the full membership"
          .formatted(properties.getMembers());
      case KUBERNETES -> properties.isKubernetesByDns()
          ? "the DNS record '%s' of a headless Kubernetes service".formatted(properties.getKubernetesServiceDns())
          : "the endpoints of the Kubernetes service '%s', read through the Kubernetes API"
              .formatted(properties.getKubernetesServiceName());
    };

  }

  /**
   * A member joined. Every member which joins or leaves is logged with the size after it. So the
   * log tells "the cache is shared" from "the cache exists".
   *
   * @param address The address of the member which joined
   * @param clusterName The cluster
   * @param memberCount The size after the change
   * @return The message
   */
  public static String memberJoined(
      final String address,
      final String clusterName,
      final int memberCount) {

    return "VanillaBP election cache: %s joined Hazelcast cluster '%s', which now has %d member(s)."
        .formatted(address, clusterName, memberCount);

  }

  /**
   * A member left, and what that costs. The hints of the partitions it held are gone, unless they
   * had a backup. A hint which is gone costs one round of asking the adapters, and that round
   * writes the hint again.
   *
   * @param address The address of the member which left
   * @param clusterName The cluster
   * @param memberCount The size after the change
   * @return The message
   */
  public static String memberLeft(
      final String address,
      final String clusterName,
      final int memberCount) {

    return """
        VanillaBP election cache: %s left Hazelcast cluster '%s', which now has %d member(s). Hints \
        which lived only there are gone, and a hint which is gone costs one probing walk."""
        .formatted(address, clusterName, memberCount);

  }

  /**
   * A call did not reach Hazelcast, and the cache answered as if it were empty.
   *
   * @param operation What was attempted
   * @param suppressed How many failures were counted without a line of their own since the
   *          last line
   * @param interval How often this is reported
   * @return The message
   */
  public static String cacheFailed(
      final String operation,
      final long suppressed,
      final Duration interval) {

    final var behind = suppressed == 0
        ? ""
        : " %d further failure(s) since the last line are not repeated.".formatted(suppressed);

    return """
        VanillaBP could not %s: Hazelcast did not answer, so the election cache answers as if it \
        were empty. Workflow operations are unaffected, they just probe the BPMS adapters again \
        instead of asking the adapter a hint names. This is reported at most once per %s.%s"""
        .formatted(operation, interval, behind);

  }

  /**
   * The member could not be started at all. The application then has the in-memory cache of the
   * platform. That cache is correct, but each node has its own.
   *
   * @param properties The configuration, for the hint which fits it
   * @return The message
   */
  public static String startFailed(
      final HazelcastElectionCacheProperties properties) {

    return """
        VanillaBP could not start its Hazelcast member and falls back to the in-memory election \
        cache, which every node keeps for itself: elections are not shared, so an operation on a \
        workflow another node elected probes the BPMS adapters again. Nothing else changes, and no \
        operation fails because of it. %s"""
        .formatted(whatToCheck(properties));

  }

  /**
   * The application brought its own Hazelcast. The cache uses it and starts no second member in
   * the same JVM.
   *
   * @param mapName The name of the map holding the hints
   * @param clusterName The cluster the application's instance is a member of
   * @return The message
   */
  public static String usingTheApplicationsInstance(
      final String mapName,
      final String clusterName) {

    return """
        VanillaBP keeps its election hints in the Hazelcast instance the application provides, map \
        '%s' of cluster '%s'. The settings under '%s' which describe a member of our own are \
        therefore not read, and neither are the backups of the map - what that map keeps is what \
        the application's own Hazelcast configuration says. The lifetimes of the hints are ours, \
        they travel with each entry. Set '%s' to false to start a member and a cluster of our own \
        instead."""
        .formatted(
            mapName,
            clusterName,
            HazelcastElectionCacheProperties.SECTION,
            HazelcastElectionCacheProperties.USE_EXISTING_INSTANCE_PROPERTY);

  }

}
