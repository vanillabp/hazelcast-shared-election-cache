![Header](./readme/vanillabp-headline.png)

# VanillaBP shared election cache on Hazelcast

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

An implementation of [VanillaBP](https://www.vanillabp.io)'s `WorkflowAdapterCache` which lets the
nodes of one application share their BPMS elections. The nodes form a small Hazelcast cluster among
themselves, so there is no component to install and none to operate.

Developers who want to **use** it read this file: it is the only documentation of this repository,
because there is not enough of it to justify a wiki. The concept behind the cache belongs to the
platform and is documented there, under
[running more than one node](https://github.com/vanillabp/adapter-platform-integration/wiki/Running-more-than-one-node)
and [the election cache](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-migration#the-election-cache).

## Documentation and supported platforms

This cache runs on both platforms VanillaBP supports:

1. **Spring Boot**<br>[![Coverage](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fvanillabp.github.io%2Fhazelcast-shared-election-cache%2Fspring-boot-report%2Findex.html&search=Total.*%3F.([0-9]%2B)[^0-9]*%3F%25&replace=%241%25&flags=m&label=Coverage&color=green&cacheSeconds=60)](https://vanillabp.github.io/hazelcast-shared-election-cache/spring-boot-report)
2. **Quarkus** (JVM mode, see [below](#a-native-image-is-refused))<br>[![Coverage](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fvanillabp.github.io%2Fhazelcast-shared-election-cache%2Fquarkus-report%2Findex.html&search=Total.*%3F.([0-9]%2B)[^0-9]*%3F%25&replace=%241%25&flags=m&label=Coverage&color=green&cacheSeconds=60)](https://vanillabp.github.io/hazelcast-shared-election-cache/quarkus-report)

Coverage is measured separately per platform, since a platform's tests never cover the other
platform's code. Click a badge to open the respective report.

## Why an application wants this

VanillaBP finds out which BPMS holds a workflow by asking the configured adapters in turn, and it
remembers the answer. That record is per node, and a node which never saw a workflow has none: it
asks every adapter again, which costs time, and in one case it costs more than time. A BPMS which
answers from an eventually consistent read model reports a workflow it created moments ago as
unknown, and what makes VanillaBP wait for it anyway is the note that this application started that
workflow there. A node without the note does not wait a shorter time, it does not wait at all, and
the operation fails with a `WorkflowNotFoundException` while the BPMS is still catching up.

Sharing the record removes that case, and it removes the repeated asking. Writing such a cache is
the same work in every project, and it is easy to get wrong in a way nobody notices until it
matters: a cache which throws when the network is partitioned turns a shortcut into an outage, and
a cache which keeps its entries forever turns a rebuilt BPMS into a routing error. So VanillaBP
ships one.

An application whose infrastructure is neither Hazelcast nor anything this repository supports still
writes its own bean implementing `WorkflowAdapterCache`, and that stays a first-class case: this
implementation is one answer to the SPI, not a replacement for it.

## Coordinates

All modules are `1.0.0-SNAPSHOT`, groupId `io.vanillabp`. The version line is this repository's own:
an upgrade of Hazelcast must not drag the platform's release with it.

|        Module         |                       Artifact                       |               Purpose                |
|-----------------------|------------------------------------------------------|--------------------------------------|
| `core/`               | `hazelcast-shared-election-cache`                    | the cache and the member it lives in |
| `spring-boot/`        | `hazelcast-shared-election-cache-spring-boot`        | Spring Boot auto-configuration       |
| `quarkus/runtime/`    | `hazelcast-shared-election-cache-quarkus`            | Quarkus extension runtime            |
| `quarkus/deployment/` | `hazelcast-shared-election-cache-quarkus-deployment` | Quarkus extension deployment         |

### Spring Boot

```xml
<dependency>
  <groupId>io.vanillabp</groupId>
  <artifactId>hazelcast-shared-election-cache-spring-boot</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Quarkus

```xml
<dependency>
  <groupId>io.vanillabp</groupId>
  <artifactId>hazelcast-shared-election-cache-quarkus</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

The dependency is the entire wiring on both platforms. VanillaBP's in-memory default steps back as
soon as this bean is present, and a cache the application defines itself still wins over both.

## How the nodes find each other

This is the one decision the cache cannot make for you, because the answer differs per environment.
Every mode answers one question: which addresses does a starting node try? The port they talk on is
`5701` by default, it is **not** the application's HTTP port, and a firewall between the nodes is
the likeliest reason a cluster does not form.

### In Kubernetes, without multicast and without any address

A headless service resolves to the addresses of its ready pods, and this is the mode which reads
exactly there. Nothing but the name of that service is configured, the addresses come and go with
the pods, and no permission on the Kubernetes API is needed:

```yaml
vanillabp:
  workflow-adapter-cache:
    hazelcast:
      discovery: kubernetes
      kubernetes-service-dns: taxi-ride.rides.svc.cluster.local
```

The service has to be **headless**, which means `clusterIP: None`. A service with a cluster IP
resolves to itself, and a starting member then finds only itself:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: taxi-ride
spec:
  clusterIP: None
  selector:
    app: taxi-ride
  ports:
    - name: hazelcast
      port: 5701
```

Where naming a service is preferred over naming a DNS record, the endpoints can be read through the
Kubernetes API instead. That needs a role which allows the pod to read endpoints, which is why it is
offered next to the DNS mode rather than instead of it:

```yaml
vanillabp:
  workflow-adapter-cache:
    hazelcast:
      discovery: kubernetes
      kubernetes-service-name: taxi-ride
      kubernetes-namespace: rides    # the pod's own namespace where unset
```

### A list of a few nodes, not of all of them

A starting node contacts the addresses it was given, and the node which answers hands over the full
membership. The list is therefore a list of seeds rather than an inventory: two of five nodes are
enough, and a node nobody lists joins like any other as long as one address it names is reachable.
Nothing has to be regenerated when the fourth node appears.

```yaml
vanillabp:
  workflow-adapter-cache:
    hazelcast:
      discovery: members
      members:
        - 10.0.0.1
        - 10.0.0.2:5701
```

### Multicast

The cheapest thing there is where it works, and it works in fewer places every year. Right for a
developer machine and for a flat network, filtered in most container networks and in every cloud:

```yaml
vanillabp:
  workflow-adapter-cache:
    hazelcast:
      discovery: multicast
```

### Auto-detection, which is the default

Hazelcast tries multicast and its cloud plugins in turn. That makes an application which just added
the dependency work on a developer machine without a line of configuration, and it usually finds
nobody in a container network. Which way actually produced the cluster is named in the startup line,
so this is a default to start from rather than one to deploy with.

## A node which finds nobody works, and shares nothing

This is the trap of a cluster the application forms itself: a node which found no other member
forms a cluster of one. Nothing fails, every call is answered, and the cache is private to that node
again, which is exactly the situation a shared cache was added to leave behind.

So the log is where you read whether the cache is shared. A working startup says it in one line:

```
VanillaBP shares its BPMS elections through Hazelcast: cluster 'taxi-ride-vanillabp-election-cache'
has 2 member(s), this one listens on [10.0.0.2]:5701, the hints live in map
'vanillabp-election-cache' with 1 backup(s) for PT1H (a workflow which ended: PT5M). The members
find each other by the addresses [10.0.0.1, 10.0.0.2:5701], which are seeds rather than the full
membership.
```

A node which is alone says so at startup and keeps saying it, every five minutes, with what to check
for the mode it is using. Every member joining and leaving is logged with the size of the cluster
after it.

## What it costs when the cache is away

Nothing fails. Every method answers as if the cache were empty: an election then probes the
configured adapters again rather than asking the adapter a hint names, which costs the walk it was
meant to save. Operations of the application are unaffected, and the residual case of the section
[Why an application wants this](#why-an-application-wants-this) is back for as long as the cache is
gone.

The failures are reported once per minute rather than once per call, because a node whose cluster is
gone fails every call and a line per call would bury the incident under its symptom. The message
names both halves: what is degraded, and that operations are not.

A member which cannot be started at all is answered the same way one level higher. The application
boots with VanillaBP's in-memory cache, per node, and a warning says so. There is no configuration
which makes an absent Hazelcast end a boot.

## Which cluster a node joins

Two applications which use the same cluster name on one network join each other, and their hints
then answer each other's questions: hints about workflows the other application never heard of,
which costs one probing walk each and is nonsense nobody wants to debug. So the cluster name is
derived from the name the application already carries, `spring.application.name` respectively
`quarkus.application.name`, plus the suffix `-vanillabp-election-cache`. An application without a
name gets `vanillabp-election-cache` and a warning naming the property to set. The name is part of
the startup line.

## Configuration

The two lifetimes of a hint are the platform's properties and are read from there, so a team moving
from the in-memory default to this cache recognises every property it already set:

|                          Property                          | Default |                                 Meaning                                 |
|------------------------------------------------------------|---------|-------------------------------------------------------------------------|
| `vanillabp.workflow-adapter-cache.time-to-live`            | `PT1H`  | how long the hint of a living workflow is kept                          |
| `vanillabp.workflow-adapter-cache.ended-time-to-live`      | `PT5M`  | how long the hint of a workflow which ended is kept                     |
| `vanillabp.workflow-adapter-cache.release-on-workflow-end` | `false` | whether the BPMS reports the end of a workflow so its hint can go early |

`max-entries` of that section is not read here. It bounds the in-memory default, and this cache is
bounded by the two lifetimes and by the heap of the cluster instead.

Everything else lives under `vanillabp.workflow-adapter-cache.hazelcast`:

|         Property          |                         Default                         |                                                                       Meaning                                                                        |
|---------------------------|---------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| `enabled`                 | `true`                                                  | whether the hints go to Hazelcast at all. `false` leaves them in the in-memory cache of the platform, per node, without the dependency being removed |
| `cluster-name`            | the application's name plus `-vanillabp-election-cache` | which cluster this node joins                                                                                                                        |
| `map-name`                | `vanillabp-election-cache`                              | the Hazelcast map holding the hints                                                                                                                  |
| `discovery`               | `auto-detect`                                           | how a starting node finds the nodes already running: `auto-detect`, `multicast`, `members`, `kubernetes`                                             |
| `members`                 | none                                                    | the seed addresses for `members`, as `host` or `host:port`. Not every node has to be listed                                                          |
| `port`                    | `5701`                                                  | the port the nodes talk to each other on                                                                                                             |
| `port-auto-increment`     | `true`                                                  | whether a node whose port is taken tries the next ones. Worth switching off where a fixed port is what the other nodes were told about               |
| `multicast-group`         | Hazelcast's own                                         | the multicast group for `multicast`                                                                                                                  |
| `multicast-port`          | Hazelcast's own                                         | the multicast port for `multicast`                                                                                                                   |
| `kubernetes-service-dns`  | none                                                    | the DNS record of a headless service, the mode which needs no address and no permission                                                              |
| `kubernetes-service-name` | none                                                    | the service whose endpoints are read through the Kubernetes API                                                                                      |
| `kubernetes-namespace`    | the pod's own                                           | the namespace of that service                                                                                                                        |
| `backup-count`            | `1`                                                     | how many copies of each partition the cluster keeps                                                                                                  |
| `use-existing-instance`   | `true`                                                  | whether a Hazelcast the application provides is used instead of starting one                                                                         |
| `alone-reminder-interval` | `PT5M`                                                  | how often a node which is alone in its cluster says so                                                                                               |
| `failure-log-interval`    | `PT1M`                                                  | how often a cache which cannot reach Hazelcast reports that it is answering as if it were empty                                                      |

A configuration which names a way of finding nodes without saying where they are ends the boot, and
the message names the property and the way out. An unconfigured application boots.

### Backups are a cost decision here

A hint is a hint, so a node which takes its partitions down with it costs the probing walk which
repairs them and nothing else. `backup-count: 0` is a legitimate setting for a cache of hints, and
`1` is the default because a rolling restart then keeps them. A split brain is safe for the same
reason: both halves keep their own hints, and no merge policy is needed.

### Using the Hazelcast the application already runs

A project which already runs Hazelcast has an instance configured, and a second member in the same
JVM would be waste plus a second cluster to reason about. So a `HazelcastInstance` the application
provides as a bean is used as it is: the map name and the two lifetimes are still ours, the cluster
and the backups of that map are the application's own configuration, and the instance keeps running
when the application shuts down, because shutting somebody else's Hazelcast down would take their
caches with it. `use-existing-instance: false` keeps the election hints in a cluster of their own
even there.

## Which Hazelcast versions

Built against **Hazelcast 5.7.0** (`com.hazelcast:hazelcast`, the Apache-2.0-licensed community
edition). The Kubernetes discovery is part of that artifact, so neither Kubernetes mode adds a
dependency of its own.

|     Version      |                                                                                                                           What it means for a rolling restart                                                                                                                           |
|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Hazelcast 5.7.x  | what this release is compiled against and tested with                                                                                                                                                                                                                                   |
| other 5.x minors | members of incompatible versions do not join each other, so an upgrade of this dependency is part of the application's rolling-restart story: while two versions run side by side, each half is its own cluster and the hints are not shared. That costs probing walks and nothing else |

Community-edition patches for vulnerabilities arrive in minor releases rather than immediately,
which is Hazelcast's own policy and worth knowing when a release is planned around a CVE.

### A native image is refused

An embedded Hazelcast member is not what a native image can hold, and this cache is a member rather
than a client because the nodes of the application are the cluster. A native Quarkus build is
therefore refused with a message naming the way out: build the application in JVM mode, or remove
the dependency and leave the election cache to VanillaBP's in-memory default, which costs one
probing walk per node.

## What the metrics show

The platform counts what the election asks of whatever cache is in use, so
`vanillabp.workflow.adapter.cache.hits`, `.misses` and `.ended.marks` keep working with this one.
The numbers only the cache itself can know do not:

- `.size` and `.size.ended` report `NaN`. Hazelcast knows the size of the map across the cluster,
  and handing it over would need an addition to the platform's SPI, which is a story of its own.
- `.evictions`, `.evictions.unused` and `.lost.hints` stay at zero, and here that is the truth
  rather than a gap: nothing evicts. This cache has no size bound, entries leave when their
  lifetime is over, and the eviction-pressure warning of the in-memory default therefore has
  nothing to warn about. What replaces it as the number to watch is `.misses` against `.hits`.

A cache which fills the heap of the cluster is the case this leaves unwatched, and until the size
is reported, Hazelcast's own metrics are where an operator sees it.

## Releasing

`Release 🚀` is started by hand with the version to release. It publishes to Maven Central from a
release branch, leaves a tag and a draft release, and opens the pull request which carries the
version bump back into main.

It refuses to run while a POM still names a SNAPSHOT, and while VanillaBP 2.0 is unreleased that is
every run: this repository's own version is set by the workflow, so a SNAPSHOT left over is a
dependency on something unreleased, which Maven Central rejects anyway. Two of them have to be
released first, the platform and the Process-Engine-API adapter the tests of the platform modules
boot against.

## Building and testing

```bash
mvn install
```

`install` and not `install verify`: install already runs every phase verify has, and the Quarkus
tests load their modules from the local Maven repository. Run `mvn spotless:apply` before
committing, it formats Markdown too.

Two members in one JVM are the cheapest cluster there is, and they are what the tests use: one node
writes a hint, another one reads it, and the election of the platform is run across both. The
failure path is a test as well, because "the cache is gone" is the behaviour this repository stands
on.

The tests of the platform modules boot a real application, and VanillaBP refuses to start without a
BPMS adapter on the classpath. The
[Process-Engine-API adapter](https://github.com/vanillabp/process-engine-api-adapter) is that
adapter here, in test scope: it runs on an in-memory engine, needs neither Docker nor network, and
is a double. Nothing about this cache depends on which BPMS an application uses.
