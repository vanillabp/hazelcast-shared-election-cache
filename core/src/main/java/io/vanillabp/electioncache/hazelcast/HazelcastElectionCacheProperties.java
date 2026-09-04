package io.vanillabp.electioncache.hazelcast;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Everything this cache needs on top of what the platform already asks for
 * (properties section
 * <code>vanillabp.workflow-adapter-cache.hazelcast</code>) - the single source of
 * truth for keys, defaults, validation and guiding warnings, used by both platform
 * integrations.
 * <p>
 * The two lifetimes are NOT here. They are
 * <code>vanillabp.workflow-adapter-cache.time-to-live</code> and
 * <code>.ended-time-to-live</code>, the keys the in-memory default reads, so a team
 * moving from that default to this cache recognises every property it already set and
 * changes none of them.
 * <p>
 * What is here is where the members are, what they call themselves and how loud they
 * are about being alone. The defaults are the ones which work on a developer machine
 * without a line of configuration; a container network needs
 * {@link #getDiscovery()}, and that is the one decision this cache cannot make for an
 * application.
 */
@Getter
@Setter
@NoArgsConstructor
public class HazelcastElectionCacheProperties {

  /**
   * How a starting member learns the addresses of the members already running. There
   * is no way which fits every environment, which is why this is a choice and not a
   * default: multicast is filtered in most container networks, a list of addresses
   * has to come from somewhere, and the Kubernetes ways only exist in Kubernetes.
   */
  public enum Discovery {

    /**
     * Let Hazelcast decide: it tries multicast and its cloud plugins in turn. Right
     * for a developer machine and for a flat network, and the reason an application
     * which adds this dependency works before anybody configured anything. In a
     * container network it usually ends in a cluster of one, which
     * {@link ElectionCacheMessages} says out loud for as long as it lasts.
     */
    AUTO_DETECT,

    /**
     * Members announce themselves on a multicast group and find each other without
     * anybody naming anybody. Nothing is cheaper where it works, and it works in
     * fewer places every year.
     */
    MULTICAST,

    /**
     * A list of SEED addresses, not an inventory: a starting member contacts the
     * addresses it was given, and the first member which answers hands over the full
     * membership. Two of five nodes are enough, and a node nobody lists still joins
     * as long as one address it names is reachable - see {@link #getMembers()}.
     */
    MEMBERS,

    /**
     * Kubernetes, either through the DNS name of a headless service (no address, no
     * multicast, no permission to read the Kubernetes API) or through the Kubernetes
     * API itself. Which of the two is decided by
     * {@link #getKubernetesServiceDns()} against
     * {@link #getKubernetesServiceName()}.
     */
    KUBERNETES;

    /**
     * The value as a property reads it, hyphenated.
     *
     * @return The configuration value naming this way of finding members
     */
    public String key() {

      return name().toLowerCase().replace('_', '-');

    }

    /**
     * Reads the value of {@link #DISCOVERY_PROPERTY}, hyphenated or not. Quarkus hands
     * the value over as it was written, and a typo in it should end the boot with the
     * four possibilities rather than with a converter's complaint.
     *
     * @param value The configured value
     * @return The way of finding members
     * @throws IllegalStateException Naming the property, the value and what is allowed
     */
    public static Discovery of(
        final String value) {

      final var normalized = value == null
          ? ""
          : value.trim().toUpperCase().replace('-', '_');

      for (final var candidate : values()) {
        if (candidate.name().equals(normalized)) {
          return candidate;
        }
      }

      throw new IllegalStateException(
          """
              The property '%s' is '%s', which is none of 'auto-detect', 'multicast', 'members' or \
              'kubernetes'! Remove the property to use '%s', which lets Hazelcast look for members \
              itself."""
              .formatted(DISCOVERY_PROPERTY, value, DEFAULT_DISCOVERY.key()));

    }

  }

  public static final String SECTION = "vanillabp.workflow-adapter-cache.hazelcast";

  public static final String ENABLED_PROPERTY = SECTION
      + ".enabled";

  public static final String CLUSTER_NAME_PROPERTY = SECTION
      + ".cluster-name";

  public static final String MAP_NAME_PROPERTY = SECTION
      + ".map-name";

  public static final String DISCOVERY_PROPERTY = SECTION
      + ".discovery";

  public static final String MEMBERS_PROPERTY = SECTION
      + ".members";

  public static final String PORT_PROPERTY = SECTION
      + ".port";

  public static final String PORT_AUTO_INCREMENT_PROPERTY = SECTION
      + ".port-auto-increment";

  public static final String MULTICAST_GROUP_PROPERTY = SECTION
      + ".multicast-group";

  public static final String MULTICAST_PORT_PROPERTY = SECTION
      + ".multicast-port";

  public static final String KUBERNETES_SERVICE_DNS_PROPERTY = SECTION
      + ".kubernetes-service-dns";

  public static final String KUBERNETES_SERVICE_NAME_PROPERTY = SECTION
      + ".kubernetes-service-name";

  public static final String KUBERNETES_NAMESPACE_PROPERTY = SECTION
      + ".kubernetes-namespace";

  public static final String BACKUP_COUNT_PROPERTY = SECTION
      + ".backup-count";

  public static final String USE_EXISTING_INSTANCE_PROPERTY = SECTION
      + ".use-existing-instance";

  public static final String ALONE_REMINDER_INTERVAL_PROPERTY = SECTION
      + ".alone-reminder-interval";

  public static final String FAILURE_LOG_INTERVAL_PROPERTY = SECTION
      + ".failure-log-interval";

  /**
   * What a derived cluster name ends with, so a Hazelcast cluster of an operator's is
   * recognisable as this cache rather than as an unknown member of their own.
   */
  public static final String CLUSTER_NAME_SUFFIX = "-vanillabp-election-cache";

  public static final String DEFAULT_MAP_NAME = "vanillabp-election-cache";

  public static final Discovery DEFAULT_DISCOVERY = Discovery.AUTO_DETECT;

  public static final int DEFAULT_PORT = 5701;

  public static final int DEFAULT_BACKUP_COUNT = 1;

  public static final Duration DEFAULT_ALONE_REMINDER_INTERVAL = Duration.ofMinutes(5);

  public static final Duration DEFAULT_FAILURE_LOG_INTERVAL = Duration.ofMinutes(1);

  /**
   * Whether the election cache runs on Hazelcast at all. Adding the dependency is what
   * switches it on, and this is how an environment which should not form a cluster (a
   * single-node installation, a test) goes back to the in-memory cache of the platform
   * without the dependency being removed.
   */
  private boolean enabled = true;

  /**
   * The name of the Hazelcast cluster the nodes of this application form. Two
   * applications which use the same name on one network join each other and then
   * answer each other's questions with hints about workflows the other one never
   * heard of - one probing walk each and no worse than that, but nonsense nobody
   * wants to debug. Unset, the name is derived from the application's own name (see
   * {@link #clusterNameOrDerived(String)}), which is what keeps two applications
   * apart without anybody configuring anything.
   */
  private String clusterName;

  /**
   * The name of the Hazelcast map holding the hints. Worth setting where an
   * application's own Hazelcast instance is used and its maps are configured by name.
   */
  private String mapName = DEFAULT_MAP_NAME;

  /**
   * How a starting member finds the members already running.
   */
  private Discovery discovery = DEFAULT_DISCOVERY;

  /**
   * The addresses a starting member tries, as <code>host</code> or
   * <code>host:port</code>, read where {@link #getDiscovery()} is
   * {@link Discovery#MEMBERS}.
   * <p>
   * <strong>Not every node has to be listed.</strong> The member which answers hands
   * over the full membership, so a couple of stable addresses are enough and a node
   * which nobody lists joins like any other. That is what makes the list survive a
   * scale-up: nothing has to be regenerated when the fourth node appears.
   */
  private List<String> members = new ArrayList<>();

  /**
   * The port this member listens on for the other members. It is not the
   * application's HTTP port and it has to be open between the nodes - a firewall or a
   * service which forwards only the HTTP port is the likeliest reason a cluster does
   * not form.
   */
  private int port = DEFAULT_PORT;

  /**
   * Whether a member whose {@link #getPort()} is taken tries the next ones. Right on
   * a developer machine running two nodes, and worth switching off in a container,
   * where a fixed port is what the other members were told about.
   */
  private boolean portAutoIncrement = true;

  /**
   * The multicast group, unset meaning Hazelcast's own default. Only read where
   * {@link #getDiscovery()} is {@link Discovery#MULTICAST}.
   */
  private String multicastGroup;

  /**
   * The multicast port, unset meaning Hazelcast's own default. Only read where
   * {@link #getDiscovery()} is {@link Discovery#MULTICAST}.
   */
  private Integer multicastPort;

  /**
   * The DNS name of a HEADLESS Kubernetes service in front of the application's pods,
   * e.g. <code>my-app.my-namespace.svc.cluster.local</code>. This is the way which
   * needs neither multicast nor any address: the service resolves to the addresses of
   * its ready pods, they come and go with the pods, and reading them needs no
   * permission on the Kubernetes API.
   */
  private String kubernetesServiceDns;

  /**
   * The name of a Kubernetes service whose endpoints are read through the Kubernetes
   * API. The alternative to {@link #getKubernetesServiceDns()} for a team which
   * prefers naming the service over naming a DNS record; it needs a role which allows
   * the pod to read endpoints.
   */
  private String kubernetesServiceName;

  /**
   * The Kubernetes namespace of {@link #getKubernetesServiceName()}, unset meaning
   * the namespace the pod itself runs in.
   */
  private String kubernetesNamespace;

  /**
   * How many copies of each partition the cluster keeps. A cost decision here and not
   * a correctness one: an entry is a hint, so a partition which goes down with its
   * node costs the probing walk which repairs it. Zero is a legitimate setting for a
   * cache of hints, one is the default because a rolling restart then keeps them.
   */
  private int backupCount = DEFAULT_BACKUP_COUNT;

  /**
   * Whether an instance the application itself provides is used instead of starting
   * one. A project which already runs Hazelcast has an instance configured, and a
   * second member in the same JVM is waste plus a second cluster to reason about.
   * Switch it off to keep the election cache in a cluster of its own even there.
   */
  private boolean useExistingInstance = true;

  /**
   * How often a member which is alone in its cluster says so. It is the one warning
   * this cache repeats, because a cluster of one looks exactly like a working
   * application and is the failure a shared cache was added to fix.
   */
  private Duration aloneReminderInterval = DEFAULT_ALONE_REMINDER_INTERVAL;

  /**
   * How often a failing cache reports that it is answering as if it were empty. One
   * line per failed call would drown the incident which caused it, so the first
   * failure of an interval is logged and the rest are counted.
   */
  private Duration failureLogInterval = DEFAULT_FAILURE_LOG_INTERVAL;

  /**
   * The cluster to join: the configured name, or one derived from the name the
   * application already carries.
   *
   * @param applicationName The application's own name (<code>spring.application.name</code>
   *          respectively <code>quarkus.application.name</code>), may be
   *          <code>null</code>
   * @return The cluster name, never <code>null</code>
   */
  public String clusterNameOrDerived(
      final String applicationName) {

    if (isSet(clusterName)) {
      return clusterName;
    }
    return isSet(applicationName)
        ? applicationName + CLUSTER_NAME_SUFFIX
        : DEFAULT_MAP_NAME;

  }

  /**
   * Whether the Kubernetes discovery reads the addresses from DNS rather than from
   * the Kubernetes API - the mode which needs no permissions.
   *
   * @return Whether a service DNS name is configured
   */
  public boolean isKubernetesByDns() {

    return isSet(kubernetesServiceDns);

  }

  /**
   * Validates what a member cannot start without, at startup and not on first use. An
   * unconfigured application boots with the defaults; a configuration which names a
   * way of finding members without saying where they are does not, because that
   * cannot be an accident which is better tolerated.
   *
   * @throws IllegalStateException Naming the offending property and the way out
   */
  public void validate() {

    if (!isSet(mapName)) {
      throw new IllegalStateException(
          """
              The property '%s' is empty! It names the Hazelcast map holding the election hints. \
              Remove the property to use the default '%s' or give the map a name of your own."""
              .formatted(MAP_NAME_PROPERTY, DEFAULT_MAP_NAME));
    }

    if (discovery == null) {
      throw new IllegalStateException(
          """
              The property '%s' is empty! It says how a starting node finds the nodes already \
              running: 'auto-detect', 'multicast', 'members' or 'kubernetes'. Remove the property \
              to use '%s'."""
              .formatted(DISCOVERY_PROPERTY, DEFAULT_DISCOVERY.key()));
    }

    if ((discovery == Discovery.MEMBERS) && members.isEmpty()) {
      throw new IllegalStateException(
          """
              The property '%s' is 'members' but '%s' names no address! Add the address of at least \
              one node which is running, as 'host' or 'host:port' - not every node has to be listed, \
              the one which answers hands over the full membership. Where no address can be known in \
              advance, use 'kubernetes' with '%s' instead."""
              .formatted(DISCOVERY_PROPERTY, MEMBERS_PROPERTY, KUBERNETES_SERVICE_DNS_PROPERTY));
    }

    if ((discovery == Discovery.KUBERNETES) && !isSet(kubernetesServiceDns) && !isSet(kubernetesServiceName)) {
      throw new IllegalStateException(
          """
              The property '%s' is 'kubernetes' but neither '%s' nor '%s' is set! Name the DNS record \
              of a HEADLESS service in front of the pods ('%s', e.g. 'my-app.my-namespace.svc.cluster.local') \
              - that way needs no address, no multicast and no permission on the Kubernetes API. Naming \
              the service instead ('%s') reads its endpoints through the Kubernetes API and needs a role \
              which allows the pod to do so."""
              .formatted(
                  DISCOVERY_PROPERTY,
                  KUBERNETES_SERVICE_DNS_PROPERTY,
                  KUBERNETES_SERVICE_NAME_PROPERTY,
                  KUBERNETES_SERVICE_DNS_PROPERTY,
                  KUBERNETES_SERVICE_NAME_PROPERTY));
    }

    if ((port < 1) || (port > 65535)) {
      throw new IllegalStateException(
          """
              The property '%s' is %d but has to be a port between 1 and 65535! It is the port the \
              nodes talk to each other on, not the application's HTTP port. Remove the property to \
              use the default %d."""
              .formatted(PORT_PROPERTY, port, DEFAULT_PORT));
    }

    if ((multicastPort != null) && ((multicastPort < 1) || (multicastPort > 65535))) {
      throw new IllegalStateException(
          """
              The property '%s' is %d but has to be a port between 1 and 65535! Remove the property \
              to use Hazelcast's own default multicast port."""
              .formatted(MULTICAST_PORT_PROPERTY, multicastPort));
    }

    if ((backupCount < 0) || (backupCount > 6)) {
      throw new IllegalStateException(
          """
              The property '%s' is %d but has to be between 0 and 6! It is how many copies of the \
              hints the cluster keeps, and it is a cost decision: an entry is a hint, so a node \
              taking its partitions down with it costs one probing walk per hint and nothing else. \
              Remove the property to use the default %d."""
              .formatted(BACKUP_COUNT_PROPERTY, backupCount, DEFAULT_BACKUP_COUNT));
    }

    validateInterval(aloneReminderInterval, ALONE_REMINDER_INTERVAL_PROPERTY, DEFAULT_ALONE_REMINDER_INTERVAL);
    validateInterval(failureLogInterval, FAILURE_LOG_INTERVAL_PROPERTY, DEFAULT_FAILURE_LOG_INTERVAL);

  }

  private void validateInterval(
      final Duration interval,
      final String property,
      final Duration defaultValue) {

    if ((interval == null) || interval.isZero() || interval.isNegative()) {
      throw new IllegalStateException(
          """
              The property '%s' is '%s' but has to be a positive duration (e.g. 'PT5M' or '30s')! \
              Remove the property to use the default of %s."""
              .formatted(property, interval, defaultValue));
    }

  }

  /**
   * What is worth saying at startup without ending it: a configuration which will
   * work and probably does not mean what it says. Reported by both platform
   * integrations, which is why the sentences live here rather than twice.
   *
   * @param applicationName The application's own name, may be <code>null</code>
   * @return The warnings, in the order they are worth reading, empty where there is
   *         nothing to warn about
   */
  public List<String> warnings(
      final String applicationName) {

    final var warnings = new ArrayList<String>();

    if (!isSet(clusterName) && !isSet(applicationName)) {
      warnings
          .add(
              """
                  The Hazelcast cluster of the VanillaBP election cache is called '%s', because \
                  neither '%s' nor a name of the application itself is set. Two applications with \
                  this default on one network join each other and start answering each other's \
                  questions - harmless, since a hint about a workflow nobody knows costs one \
                  probing walk, but nobody wants to debug it. Set '%s' (or name the application, \
                  which is where the cluster name is derived from)."""
                  .formatted(DEFAULT_MAP_NAME, CLUSTER_NAME_PROPERTY, CLUSTER_NAME_PROPERTY));
    }

    if (!members.isEmpty() && (discovery != Discovery.MEMBERS)) {
      warnings
          .add(
              """
                  '%s' names %d address(es) which nobody reads: '%s' is '%s'. Set it to 'members' \
                  to use the list."""
                  .formatted(
                      MEMBERS_PROPERTY,
                      members.size(),
                      DISCOVERY_PROPERTY,
                      discovery.key()));
    }

    if ((discovery == Discovery.KUBERNETES) && isSet(kubernetesServiceDns) && isSet(kubernetesServiceName)) {
      warnings
          .add(
              """
                  Both '%s' and '%s' are set, and the DNS record wins. Remove '%s' to make the \
                  configuration say what happens, or remove '%s' to read the endpoints through the \
                  Kubernetes API instead (which needs a role allowing the pod to do so)."""
                  .formatted(
                      KUBERNETES_SERVICE_DNS_PROPERTY,
                      KUBERNETES_SERVICE_NAME_PROPERTY,
                      KUBERNETES_SERVICE_NAME_PROPERTY,
                      KUBERNETES_SERVICE_DNS_PROPERTY));
    }

    return warnings;

  }

  private static boolean isSet(
      final String value) {

    return (value != null) && !value.isBlank();

  }

}
