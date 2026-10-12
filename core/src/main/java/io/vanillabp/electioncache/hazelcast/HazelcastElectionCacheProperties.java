package io.vanillabp.electioncache.hazelcast;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything this cache needs on top of what the platform already asks for. The properties
 * section is <code>vanillabp.workflow-adapter-cache.hazelcast</code>. This class is the one
 * place for its keys, defaults, checks and guiding warnings, and both platform integrations use
 * it.
 * <p>
 * The two lifetimes are NOT here. They are
 * <code>vanillabp.workflow-adapter-cache.time-to-live</code> and
 * <code>.ended-time-to-live</code>, the keys which the in-memory default reads. So a team which
 * moves from that default to this cache knows every property it already set, and changes none of
 * them.
 * <p>
 * What is here: where the members are, what their cluster is called, and how loudly a member
 * says that it is alone. The defaults work on a developer machine without a line of
 * configuration. A container network needs a value for {@link #DISCOVERY_PROPERTY}. That is the
 * one decision this cache cannot make for an application.
 */
public class HazelcastElectionCacheProperties {

  /**
   * Builds the section the way an application has it which configured nothing. The platform
   * integration passes the values it found to the setters. So every field which a deployment does
   * not set keeps the default written next to it.
   */
  public HazelcastElectionCacheProperties() {

  }

  /**
   * How a starting member learns the addresses of the members which already run. No way fits
   * every environment, so this is a choice. Most container networks filter multicast. A list of
   * addresses has to come from somewhere. And the Kubernetes ways only work in Kubernetes.
   */
  public enum Discovery {

    /**
     * Lets Hazelcast decide. It tries multicast and its cloud plugins in turn. This is right for
     * a developer machine and for a flat network. It is also why an application which adds this
     * dependency works before anybody configured anything. In a container network it usually ends
     * in a cluster of one. {@link ElectionCacheMessages} then warns about it for as long as it
     * lasts.
     */
    AUTO_DETECT,

    /**
     * Members announce themselves on a multicast group, and they find each other without anybody
     * naming an address. Nothing is cheaper where it works. But it works in fewer places every
     * year.
     */
    MULTICAST,

    /**
     * A list of SEED addresses, not a list of all members. A starting member contacts the
     * addresses it was given, and the first member which answers hands over all the others. Two
     * of five nodes are enough. A node which nobody lists still joins, as long as one address it
     * names can be reached. See {@link #MEMBERS_PROPERTY}.
     */
    MEMBERS,

    /**
     * Kubernetes, in one of two ways. Either through the DNS name of a headless service, which
     * needs no address, no multicast and no permission to read the Kubernetes API. Or through the
     * Kubernetes API, with {@link #KUBERNETES_SERVICE_NAME_PROPERTY}. If
     * {@link #KUBERNETES_SERVICE_DNS_PROPERTY} is set, the DNS name is used.
     */
    KUBERNETES;

    /**
     * The value as a property writes it, with hyphens.
     *
     * @return The configuration value naming this way of finding members
     */
    public String key() {

      return name().toLowerCase().replace('_', '-');

    }

    /**
     * Reads the value of {@link #DISCOVERY_PROPERTY}, with or without hyphens. Quarkus passes
     * the value on as it was written. A typo in it should stop the start with a list of the four
     * allowed values, not with the complaint of a converter.
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

  /**
   * The section which holds the properties of this cache. Every key below is built from it, so
   * the prefix is written once. Every guiding message spells a key from these constants. A
   * literal in a message could drift away from the binding.
   */
  public static final String SECTION = "vanillabp.workflow-adapter-cache.hazelcast";

  /**
   * The key which says whether the cache runs on Hazelcast at all. Spring Boot reads it as the
   * condition of the auto-configuration. That is why it is a constant.
   */
  public static final String ENABLED_PROPERTY = SECTION
      + ".enabled";

  /**
   * The key naming the Hazelcast cluster the nodes of the application form.
   */
  public static final String CLUSTER_NAME_PROPERTY = SECTION
      + ".cluster-name";

  /**
   * The key naming the Hazelcast map the hints live in.
   */
  public static final String MAP_NAME_PROPERTY = SECTION
      + ".map-name";

  /**
   * The key which says how a starting member finds the members which already run. It is the one
   * value this cache cannot guess for a container network.
   */
  public static final String DISCOVERY_PROPERTY = SECTION
      + ".discovery";

  /**
   * The key holding the addresses a starting member tries.
   */
  public static final String MEMBERS_PROPERTY = SECTION
      + ".members";

  /**
   * The key naming the port the members talk to each other on.
   */
  public static final String PORT_PROPERTY = SECTION
      + ".port";

  /**
   * The key which says whether a member whose port is taken tries the next ones.
   */
  public static final String PORT_AUTO_INCREMENT_PROPERTY = SECTION
      + ".port-auto-increment";

  /**
   * The key naming the multicast group. It is read only if the discovery is multicast.
   */
  public static final String MULTICAST_GROUP_PROPERTY = SECTION
      + ".multicast-group";

  /**
   * The key naming the multicast port. It is read only if the discovery is multicast.
   */
  public static final String MULTICAST_PORT_PROPERTY = SECTION
      + ".multicast-port";

  /**
   * The key naming the DNS record of a headless Kubernetes service in front of the pods.
   */
  public static final String KUBERNETES_SERVICE_DNS_PROPERTY = SECTION
      + ".kubernetes-service-dns";

  /**
   * The key naming the Kubernetes service whose endpoints are read through the API.
   */
  public static final String KUBERNETES_SERVICE_NAME_PROPERTY = SECTION
      + ".kubernetes-service-name";

  /**
   * The key naming the namespace of that service.
   */
  public static final String KUBERNETES_NAMESPACE_PROPERTY = SECTION
      + ".kubernetes-namespace";

  /**
   * The key which says how many copies of each partition the cluster keeps.
   */
  public static final String BACKUP_COUNT_PROPERTY = SECTION
      + ".backup-count";

  /**
   * The key which says whether a Hazelcast which the application already provides is used.
   */
  public static final String USE_EXISTING_INSTANCE_PROPERTY = SECTION
      + ".use-existing-instance";

  /**
   * The key which says how often a member which is alone in its cluster repeats its warning.
   */
  public static final String ALONE_REMINDER_INTERVAL_PROPERTY = SECTION
      + ".alone-reminder-interval";

  /**
   * The key which says how often a failing cache reports that it answers as if it were empty.
   */
  public static final String FAILURE_LOG_INTERVAL_PROPERTY = SECTION
      + ".failure-log-interval";

  /**
   * The end of a cluster name made from the application name. An operator who runs a Hazelcast
   * cluster of their own then sees that this cluster belongs to the cache, and not to some
   * unknown member of theirs.
   */
  public static final String CLUSTER_NAME_SUFFIX = "-vanillabp-election-cache";

  /**
   * The map name if nothing is configured. It is also the cluster name of last resort. An
   * application without a name of its own gives nothing to make a cluster name from. And two
   * made-up names would be two things to look up in a log ({@link #clusterNameOrDerived(String)}).
   */
  public static final String DEFAULT_MAP_NAME = "vanillabp-election-cache";

  /**
   * The way of finding members if nothing is configured. It needs no configuration of its own. So
   * an application works on a developer machine as soon as it adds this dependency.
   */
  public static final Discovery DEFAULT_DISCOVERY = Discovery.AUTO_DETECT;

  /**
   * The port if nothing is configured. It is the port Hazelcast itself starts on. So a member of
   * this cache sits where an operator expects a Hazelcast member to be.
   */
  public static final int DEFAULT_PORT = 5701;

  /**
   * The number of copies if nothing is configured. It is one, so a rolling restart keeps the
   * hints. Zero is correct as well. It costs one round of asking the adapters for each hint which
   * a leaving node took with it.
   */
  public static final int DEFAULT_BACKUP_COUNT = 1;

  /**
   * The reminder interval if nothing is configured. It is often enough that somebody who did not
   * read the startup log still sees the warning. And it is rare enough that the warning does not
   * become noise itself.
   */
  public static final Duration DEFAULT_ALONE_REMINDER_INTERVAL = Duration.ofMinutes(5);

  /**
   * The failure interval if nothing is configured. It is shorter than the reminder of a member
   * which is alone. A cache which stops answering is news, but being alone is a state which lasts.
   */
  public static final Duration DEFAULT_FAILURE_LOG_INTERVAL = Duration.ofMinutes(1);

  /**
   * Whether the election cache runs on Hazelcast at all. Adding the dependency switches it on.
   * Some environments should not form a cluster, like an installation with one node, or a test.
   * With this switch they go back to the in-memory cache of the platform and keep the dependency.
   */
  private boolean enabled = true;

  /**
   * The name of the Hazelcast cluster which the nodes of this application form. Two applications
   * which use the same name on one network join each other. Then they answer each other with
   * hints about workflows the other one never heard of. That costs one round of asking the
   * adapters each and nothing worse, but nobody wants to debug such nonsense. If the name is not
   * set, it is made from the name of the application (see {@link #clusterNameOrDerived(String)}).
   * That keeps two applications apart without any configuration.
   */
  private String clusterName;

  /**
   * The name of the Hazelcast map which holds the hints. Set it if the cache uses the Hazelcast
   * instance of the application, and that instance configures its maps by name.
   */
  private String mapName = DEFAULT_MAP_NAME;

  /**
   * How a starting member finds the members already running.
   */
  private Discovery discovery = DEFAULT_DISCOVERY;

  /**
   * The addresses a starting member tries, as <code>host</code> or <code>host:port</code>. They
   * are read only if {@link #DISCOVERY_PROPERTY} is {@link Discovery#MEMBERS}.
   * <p>
   * <strong>Not every node has to be listed.</strong> The member which answers hands over all the
   * other members. So a few stable addresses are enough, and a node which nobody lists joins like
   * any other. That is why the list still works when the application scales up. Nothing has to
   * be rebuilt when a fourth node appears.
   */
  private List<String> members = new ArrayList<>();

  /**
   * The port this member listens on for the other members. It is not the HTTP port of the
   * application, and it has to be open between the nodes. The most likely reason why a cluster
   * does not form is a firewall, or a service which forwards only the HTTP port.
   */
  private int port = DEFAULT_PORT;

  /**
   * Whether a member whose port is taken tries the next ones. That is right on a developer
   * machine which runs two nodes. Switch it off in a container. There the other members were told
   * about a fixed port.
   */
  private boolean portAutoIncrement = true;

  /**
   * The multicast group. If it is not set, Hazelcast uses its own default. It is read only if
   * {@link #DISCOVERY_PROPERTY} is {@link Discovery#MULTICAST}.
   */
  private String multicastGroup;

  /**
   * The multicast port. If it is not set, Hazelcast uses its own default. It is read only if
   * {@link #DISCOVERY_PROPERTY} is {@link Discovery#MULTICAST}.
   */
  private Integer multicastPort;

  /**
   * The DNS name of a HEADLESS Kubernetes service in front of the pods of the application, e.g.
   * <code>my-app.my-namespace.svc.cluster.local</code>. This way needs neither multicast nor any
   * address. The service resolves to the addresses of its ready pods, and they come and go with
   * the pods. Reading them needs no permission on the Kubernetes API.
   */
  private String kubernetesServiceDns;

  /**
   * The name of a Kubernetes service whose endpoints are read through the Kubernetes API. It is
   * the other choice to {@link #KUBERNETES_SERVICE_DNS_PROPERTY}, for a team which would rather
   * name the service than a DNS record. It needs a role which allows the pod to read endpoints.
   */
  private String kubernetesServiceName;

  /**
   * The Kubernetes namespace of {@link #KUBERNETES_SERVICE_NAME_PROPERTY}. If it is not set, the
   * namespace of the pod itself is used.
   */
  private String kubernetesNamespace;

  /**
   * How many copies of each partition the cluster keeps. Here this decides about cost, not about
   * correctness. An entry is only a hint. So a partition which goes down with its node costs one
   * round of asking the adapters, and that round writes the hint again. Zero is a valid setting
   * for a cache of hints. One is the default, because then a rolling restart keeps the hints.
   */
  private int backupCount = DEFAULT_BACKUP_COUNT;

  /**
   * Whether an instance which the application provides is used, instead of starting one. A
   * project which already runs Hazelcast has an instance configured. A second member in the same
   * JVM wastes resources, and it is a second cluster to think about. Switch this off to keep the
   * election cache in a cluster of its own anyway.
   */
  private boolean useExistingInstance = true;

  /**
   * How often a member which is alone in its cluster says so. It is the only warning this cache
   * repeats. A cluster of one looks exactly like a working application, and it is the very
   * failure which the shared cache was added to fix.
   */
  private Duration aloneReminderInterval = DEFAULT_ALONE_REMINDER_INTERVAL;

  /**
   * How often a failing cache reports that it answers as if it were empty. One line per failed
   * call would hide the cause of the failures. So the first failure of an interval is logged, and
   * the rest are counted.
   */
  private Duration failureLogInterval = DEFAULT_FAILURE_LOG_INTERVAL;

  /**
   * Whether the election cache runs on Hazelcast at all.
   *
   * @return Whether the election cache runs on Hazelcast
   */
  public boolean isEnabled() {

    return enabled;

  }

  /**
   * Whether the election cache runs on Hazelcast at all.
   *
   * @param enabled Whether the election cache runs on Hazelcast
   */
  public void setEnabled(
      final boolean enabled) {

    this.enabled = enabled;

  }

  /**
   * The name of the Hazelcast cluster the nodes of this application form.
   *
   * @return The configured cluster name, <code>null</code> where none is set
   */
  public String getClusterName() {

    return clusterName;

  }

  /**
   * The name of the Hazelcast cluster the nodes of this application form.
   *
   * @param clusterName The name of the Hazelcast cluster, <code>null</code> to derive one
   */
  public void setClusterName(
      final String clusterName) {

    this.clusterName = clusterName;

  }

  /**
   * The name of the Hazelcast map holding the hints.
   *
   * @return The name of the Hazelcast map holding the hints
   */
  public String getMapName() {

    return mapName;

  }

  /**
   * The name of the Hazelcast map holding the hints.
   *
   * @param mapName The name of the Hazelcast map holding the hints
   */
  public void setMapName(
      final String mapName) {

    this.mapName = mapName;

  }

  /**
   * How a starting member finds the members already running.
   *
   * @return How a starting member finds the members already running
   */
  public Discovery getDiscovery() {

    return discovery;

  }

  /**
   * How a starting member finds the members already running.
   *
   * @param discovery How a starting member finds the members already running
   */
  public void setDiscovery(
      final Discovery discovery) {

    this.discovery = discovery;

  }

  /**
   * The addresses a starting member tries.
   *
   * @return The addresses a starting member tries, never <code>null</code>
   */
  public List<String> getMembers() {

    return members;

  }

  /**
   * The addresses a starting member tries.
   *
   * @param members The addresses a starting member tries
   */
  public void setMembers(
      final List<String> members) {

    this.members = members;

  }

  /**
   * The port this member listens on for the other members.
   *
   * @return The port this member listens on for the other members
   */
  public int getPort() {

    return port;

  }

  /**
   * The port this member listens on for the other members.
   *
   * @param port The port this member listens on for the other members
   */
  public void setPort(
      final int port) {

    this.port = port;

  }

  /**
   * Whether a member whose port is taken tries the next ones.
   *
   * @return Whether a member whose port is taken tries the next ones
   */
  public boolean isPortAutoIncrement() {

    return portAutoIncrement;

  }

  /**
   * Whether a member whose port is taken tries the next ones.
   *
   * @param portAutoIncrement Whether a member whose port is taken tries the next ones
   */
  public void setPortAutoIncrement(
      final boolean portAutoIncrement) {

    this.portAutoIncrement = portAutoIncrement;

  }

  /**
   * The multicast group. It is read only if the discovery is multicast.
   *
   * @return The multicast group, <code>null</code> for Hazelcast's own default
   */
  public String getMulticastGroup() {

    return multicastGroup;

  }

  /**
   * The multicast group. It is read only if the discovery is multicast.
   *
   * @param multicastGroup The multicast group, <code>null</code> for Hazelcast's own default
   */
  public void setMulticastGroup(
      final String multicastGroup) {

    this.multicastGroup = multicastGroup;

  }

  /**
   * The multicast port. It is read only if the discovery is multicast.
   *
   * @return The multicast port, <code>null</code> for Hazelcast's own default
   */
  public Integer getMulticastPort() {

    return multicastPort;

  }

  /**
   * The multicast port. It is read only if the discovery is multicast.
   *
   * @param multicastPort The multicast port, <code>null</code> for Hazelcast's own default
   */
  public void setMulticastPort(
      final Integer multicastPort) {

    this.multicastPort = multicastPort;

  }

  /**
   * The DNS name of a headless Kubernetes service in front of the pods.
   *
   * @return The DNS name of the headless service, <code>null</code> where none is set
   */
  public String getKubernetesServiceDns() {

    return kubernetesServiceDns;

  }

  /**
   * The DNS name of a headless Kubernetes service in front of the pods.
   *
   * @param kubernetesServiceDns The DNS name of a headless service in front of the pods
   */
  public void setKubernetesServiceDns(
      final String kubernetesServiceDns) {

    this.kubernetesServiceDns = kubernetesServiceDns;

  }

  /**
   * The name of a Kubernetes service whose endpoints are read through the API.
   *
   * @return The name of the Kubernetes service, <code>null</code> where none is set
   */
  public String getKubernetesServiceName() {

    return kubernetesServiceName;

  }

  /**
   * The name of a Kubernetes service whose endpoints are read through the API.
   *
   * @param kubernetesServiceName The name of the Kubernetes service whose endpoints are read
   */
  public void setKubernetesServiceName(
      final String kubernetesServiceName) {

    this.kubernetesServiceName = kubernetesServiceName;

  }

  /**
   * The Kubernetes namespace of that service.
   *
   * @return The namespace of that service, <code>null</code> for the pod's own
   */
  public String getKubernetesNamespace() {

    return kubernetesNamespace;

  }

  /**
   * The Kubernetes namespace of that service.
   *
   * @param kubernetesNamespace The namespace of that service, <code>null</code> for the pod's own
   */
  public void setKubernetesNamespace(
      final String kubernetesNamespace) {

    this.kubernetesNamespace = kubernetesNamespace;

  }

  /**
   * How many copies of each partition the cluster keeps.
   *
   * @return How many copies of each partition the cluster keeps
   */
  public int getBackupCount() {

    return backupCount;

  }

  /**
   * How many copies of each partition the cluster keeps.
   *
   * @param backupCount How many copies of each partition the cluster keeps
   */
  public void setBackupCount(
      final int backupCount) {

    this.backupCount = backupCount;

  }

  /**
   * Whether an instance the application itself provides is used.
   *
   * @return Whether an instance the application provides is used
   */
  public boolean isUseExistingInstance() {

    return useExistingInstance;

  }

  /**
   * Whether an instance the application itself provides is used.
   *
   * @param useExistingInstance Whether an instance the application provides is used
   */
  public void setUseExistingInstance(
      final boolean useExistingInstance) {

    this.useExistingInstance = useExistingInstance;

  }

  /**
   * How often a member which is alone in its cluster says so.
   *
   * @return How often a member which is alone says so
   */
  public Duration getAloneReminderInterval() {

    return aloneReminderInterval;

  }

  /**
   * How often a member which is alone in its cluster says so.
   *
   * @param aloneReminderInterval How often a member which is alone says so
   */
  public void setAloneReminderInterval(
      final Duration aloneReminderInterval) {

    this.aloneReminderInterval = aloneReminderInterval;

  }

  /**
   * How often a failing cache reports that it answers as if it were empty.
   *
   * @return How often a failing cache reports that it answers as if it were empty
   */
  public Duration getFailureLogInterval() {

    return failureLogInterval;

  }

  /**
   * How often a failing cache reports that it answers as if it were empty.
   *
   * @param failureLogInterval How often a failing cache reports that it answers as if it were empty
   */
  public void setFailureLogInterval(
      final Duration failureLogInterval) {

    this.failureLogInterval = failureLogInterval;

  }

  /**
   * The cluster to join: the configured name, or a name made from the name of the application.
   *
   * @param applicationName The name of the application (<code>spring.application.name</code> or
   *          <code>quarkus.application.name</code>). May be <code>null</code>
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
   * Whether the Kubernetes discovery reads the addresses from DNS, not from the Kubernetes API.
   * This mode needs no permissions.
   *
   * @return Whether a service DNS name is configured
   */
  public boolean isKubernetesByDns() {

    return isSet(kubernetesServiceDns);

  }

  /**
   * Checks what a member cannot start without, at startup and not on first use. An application
   * which configured nothing starts with the defaults. A configuration which names a way of
   * finding members, but not where they are, stops the start. That cannot be an accident which is
   * better ignored.
   *
   * @throws IllegalStateException Naming the wrong property and how to fix it
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
   * What is worth a warning at startup without stopping it: a configuration which works, but
   * probably does not mean what it says. Both platform integrations log these. So the sentences
   * are kept here, not twice.
   *
   * @param applicationName The name of the application, may be <code>null</code>
   * @return The warnings, most important first. Empty if there is nothing to warn about
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
