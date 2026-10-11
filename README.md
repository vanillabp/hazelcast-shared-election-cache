![Header](./readme/vanillabp-headline.png)

# VanillaBP shared election cache on Hazelcast

[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

An implementation of [VanillaBP](https://www.vanillabp.io)'s `WorkflowAdapterCache`. With it, the
nodes of one application share their BPMS elections. A BPMS election is VanillaBP finding out which
BPMS holds a workflow. The nodes form a small Hazelcast cluster among themselves, so you install
nothing new and operate nothing new.

If you want to **use** the cache, read this file. It is the only documentation of this repository,
because there is too little to fill a wiki. The idea behind the cache belongs to the platform, and
the platform wiki explains it under
[running more than one node](https://github.com/vanillabp/adapter-platform-integration/wiki/Running-more-than-one-node)
and [the election cache](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-migration#the-election-cache).

## Documentation and supported platforms

This cache runs on both platforms VanillaBP supports:

1. **Spring Boot**<br>[![Coverage](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fvanillabp.github.io%2Fhazelcast-shared-election-cache%2Fspring-boot-report%2Findex.html&search=Total.*%3F.([0-9]%2B)[^0-9]*%3F%25&replace=%241%25&flags=m&label=Coverage&color=green&cacheSeconds=60)](https://vanillabp.github.io/hazelcast-shared-election-cache/spring-boot-report)
2. **Quarkus** (JVM mode, see [below](#a-native-image-is-refused))<br>[![Coverage](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fvanillabp.github.io%2Fhazelcast-shared-election-cache%2Fquarkus-report%2Findex.html&search=Total.*%3F.([0-9]%2B)[^0-9]*%3F%25&replace=%241%25&flags=m&label=Coverage&color=green&cacheSeconds=60)](https://vanillabp.github.io/hazelcast-shared-election-cache/quarkus-report)

Coverage is measured for each platform on its own, because the tests of one platform never run
the code of the other. Click a badge to open its report.

## Why an application wants this

VanillaBP finds out which BPMS holds a workflow by asking the configured adapters one after the
other. Then it remembers the answer. Each node keeps its own record, so a node which never saw a
workflow knows nothing about it. It asks every adapter again. That costs time, and in one case it
costs more than time.

Some BPMS answer from a read model which is only eventually consistent: it catches up with the
engine a little later. Such a BPMS reports a workflow it created moments ago as unknown. VanillaBP
waits for it anyway, because it has a note that this application started the workflow there. A
node without that note does not wait at all. The operation fails with a `WorkflowNotFoundException`
while the BPMS is still catching up.

A shared record removes that case, and it removes the repeated asking. Every project would have to
write such a cache, and it is easy to get wrong without anybody noticing. A cache which throws when
the network splits turns a shortcut into an outage. A cache which keeps its entries forever turns a
rebuilt BPMS into a routing error. So VanillaBP ships one.

Your infrastructure may be neither Hazelcast nor anything else this repository supports. Then you
write your own bean which implements `WorkflowAdapterCache`, and that is fully supported. This
implementation is one answer to the SPI, not a replacement for it.

## Coordinates

All modules are `1.0.0-SNAPSHOT`, groupId `io.vanillabp`. This repository has its own version
numbers, so an upgrade of Hazelcast does not force a release of the platform.

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

On both platforms the dependency is all the wiring you need. VanillaBP's in-memory default steps
back as soon as this bean is there. A cache the application defines itself still wins over both.

## How the nodes find each other

The cache cannot make this decision for you, because the answer is different in every
environment. Each mode answers one question: which addresses does a starting node try? The nodes
talk on port `5701` by default. That is **not** the application's HTTP port. If a cluster does not
form, the most likely reason is a firewall between the nodes.

### In Kubernetes, without multicast and without any address

A headless service resolves to the addresses of its ready pods, and this mode reads exactly that.
You configure only the name of the service. The addresses come and go with the pods, and you need
no permission on the Kubernetes API:

```yaml
vanillabp:
  workflow-adapter-cache:
    hazelcast:
      discovery: kubernetes
      kubernetes-service-dns: taxi-ride.rides.svc.cluster.local
```

The service has to be **headless**, which means `clusterIP: None`. A service with a cluster IP
resolves to itself, so a starting member finds only itself:

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

If you would rather name a service than a DNS record, the cache can read the endpoints through the
Kubernetes API. That needs a role which allows the pod to read endpoints. So this mode is offered
next to the DNS mode, not instead of it:

```yaml
vanillabp:
  workflow-adapter-cache:
    hazelcast:
      discovery: kubernetes
      kubernetes-service-name: taxi-ride
      kubernetes-namespace: rides    # the pod's own namespace where unset
```

### A list of a few nodes, not of all of them

A starting node contacts the addresses it was given. The node which answers hands over the list of
all members. So the list you configure holds seeds, not every node. Two of five nodes are enough. A
node nobody lists joins like any other, as long as one of the addresses it knows is reachable.
Nothing has to be generated again when a fourth node appears.

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

The cheapest mode where it works, and it works in fewer places every year. Use it on a developer
machine and in a flat network. Most container networks and every cloud filter it out:

```yaml
vanillabp:
  workflow-adapter-cache:
    hazelcast:
      discovery: multicast
```

### Auto-detection, which is the default

Hazelcast tries multicast and its cloud plugins one after the other. So an application which just
added the dependency works on a developer machine without any configuration. In a container network
it usually finds nobody. The startup line names the way which actually formed the cluster. Use this
default to get started, not to deploy.

## A node which finds nobody works, and shares nothing

This is the trap of a cluster the application forms itself. A node which found no other member
forms a cluster of one. Nothing fails and every call is answered. But the cache belongs to that node
alone again, and that is exactly what the shared cache was meant to fix.

So read the log to see whether the cache is shared. A working startup says it in one line:

```
VanillaBP shares its BPMS elections through Hazelcast: cluster 'taxi-ride-vanillabp-election-cache'
has 2 member(s), this one listens on [10.0.0.2]:5701, the hints live in map
'vanillabp-election-cache' with 1 backup(s) for PT1H (a workflow which ended: PT5M). The members
find each other by the addresses [10.0.0.1, 10.0.0.2:5701], which are seeds rather than the full
membership.
```

A node which is alone says so at startup. It keeps saying it every five minutes, together with what
to check for the mode it uses. Each member which joins or leaves is logged with the new size of the
cluster.

## What it costs when the cache is away

Nothing fails. Every method answers as if the cache were empty. An election then asks the
configured adapters again, instead of only the adapter a hint names. That costs the walk the cache
was meant to save. The operations of the application keep working. For as long as the cache is
gone, the remaining case from
[Why an application wants this](#why-an-application-wants-this) is back.

The failures are reported once per minute, not once per call. A node whose cluster is gone fails on
every call, and a line per call would bury the real problem under its symptoms. The message says
two things: what is degraded, and that operations are not.

A member which cannot start at all is handled the same way, one level higher. The application boots
with VanillaBP's in-memory cache, one per node, and a warning says so. No configuration makes a boot
fail because Hazelcast is missing.

## Which cluster a node joins

Two applications which use the same cluster name on one network join each other. Then the hints of
one answer the questions of the other. They are hints about workflows the other application never
heard of. Each one costs a probing walk, and nobody wants to debug that. So the cluster name comes
from the name the application already has: `spring.application.name` or
`quarkus.application.name`, plus the suffix `-vanillabp-election-cache`. An application without a
name gets `vanillabp-election-cache` and a warning which names the property to set. The startup line
shows the name.

## Configuration

The two lifetimes of a hint are properties of the platform, and the cache reads them from there. A
team which moves from the in-memory default to this cache finds every property it already set:

|                          Property                          | Default |                                 Meaning                                 |
|------------------------------------------------------------|---------|-------------------------------------------------------------------------|
| `vanillabp.workflow-adapter-cache.time-to-live`            | `PT1H`  | how long the hint of a living workflow is kept                          |
| `vanillabp.workflow-adapter-cache.ended-time-to-live`      | `PT5M`  | how long the hint of a workflow which ended is kept                     |
| `vanillabp.workflow-adapter-cache.release-on-workflow-end` | `false` | whether the BPMS reports the end of a workflow so its hint can go early |

This cache does not read `max-entries` of that section. It limits the size of the in-memory
default. This cache is limited by the two lifetimes and by the heap of the cluster instead.

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

A configuration may name a way of finding nodes but not say where they are. Then the boot ends,
and the message names the property and how to fix it. An application without any configuration
boots.

### Backups are a cost decision here

A hint is only a hint. A node which goes down takes its partitions with it, and that costs the
probing walk which repairs them, nothing else. So `backup-count: 0` is a valid setting for a cache of
hints. The default is `1`, because then a rolling restart keeps the hints. A split brain, where the
cluster falls apart into two halves, is safe for the same reason. Both halves keep their own hints,
and no merge policy is needed.

### Using the Hazelcast the application already runs

A project which already runs Hazelcast has an instance configured. A second member in the same JVM
would be a waste, and one more cluster to think about. So the cache uses a `HazelcastInstance` which
the application provides as a bean, as it is. The map name and the two lifetimes still come from
this cache. The cluster and the backups of that map come from the application's own configuration.
The instance keeps running when the application shuts down, because shutting down somebody else's
Hazelcast would take their caches with it. With `use-existing-instance: false` the election hints
get a cluster of their own even then.

## Which Hazelcast versions

Built against **Hazelcast 5.7.0** (`com.hazelcast:hazelcast`, the community edition under the Apache
2.0 license). The Kubernetes discovery is part of that artifact, so neither Kubernetes mode adds a
dependency.

|     Version      |                                                                                                                           What it means for a rolling restart                                                                                                                           |
|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Hazelcast 5.7.x  | what this release is compiled against and tested with                                                                                                                                                                                                                                   |
| other 5.x minors | members of incompatible versions do not join each other, so an upgrade of this dependency is part of the application's rolling-restart story: while two versions run side by side, each half is its own cluster and the hints are not shared. That costs probing walks and nothing else |

Fixes for security holes reach the community edition in minor releases, not right away. That is
Hazelcast's own policy. Keep it in mind when you plan a release around a CVE (a published security
issue).

### A native image is refused

A native image cannot hold an embedded Hazelcast member. This cache is a member and not a client,
because the nodes of the application are the cluster. So a native Quarkus build is refused, and the
message names the way out. Build the application in JVM mode. Or remove the dependency and leave the
election cache to VanillaBP's in-memory default, which costs one probing walk per node.

## What the metrics show

The platform counts what the election asked of the cache, whichever cache is in use. So
`vanillabp.workflow.adapter.cache.hits`, `.misses` and `.ended.marks` are reported with this cache
as with any other. Watch these three. Misses against hits tell you whether the hints do their job.
`.ended.marks` tells you whether the end of a workflow reaches the cache at all.

A number which only one implementation can know carries the name of that implementation. So the
numbers of VanillaBP's in-memory default (`vanillabp.inmemory.election.cache.*`, its size and its
evictions) are missing while this cache is in use, and nothing pretends otherwise. Most of them
would mean nothing here anyway. This cache has no size limit, so it never evicts anything, and there
is no eviction pressure to warn about.

This cache's own numbers are still missing. Hazelcast knows the size of the map across the cluster,
the number of members and whether this node is alone. An operator most wants an alert on the last
one. Publishing them under `vanillabp.hazelcast.election.cache.*` needs an optional Micrometer
dependency on each platform, and that is a story of its own. Until then you see a cluster of one in
the log, and the heap of the map in Hazelcast's own metrics.

## Releasing

You start `Release 🚀` by hand with the version to release. It publishes to Maven Central from a
release branch and leaves a tag and a draft release. Then it opens the pull request which brings the
new version number back into main.

It refuses to run while a POM still names a SNAPSHOT. Until VanillaBP 2.0 is released, that is every
run. The workflow sets this repository's own version, so a SNAPSHOT which is left is a dependency on
something unreleased, and Maven Central rejects that anyway. Two of them have to be released first:
the platform, and the Process-Engine-API adapter which the tests of the platform modules boot
with.

## Building and testing

```bash
mvn install
```

`install` and not `install verify`: install already runs every phase of verify, and the Quarkus
tests load their modules from the local Maven repository. Run `mvn spotless:apply` before you
commit. It formats Markdown too.

Two members in one JVM are the cheapest cluster there is, and the tests use exactly that. One node
writes a hint, another one reads it, and the election of the platform runs across both. The failure
path has a test as well, because this repository depends on how it behaves when the cache is gone.

The tests of the platform modules boot a real application, and VanillaBP refuses to start without a
BPMS adapter on the classpath. Here the
[Process-Engine-API adapter](https://github.com/vanillabp/process-engine-api-adapter) is that
adapter, in test scope. It runs on an in-memory engine, needs neither Docker nor a network, and is
only a stand-in. Nothing about this cache depends on which BPMS an application uses.

What a pull request needs beyond a green build is in [`CONTRIBUTING.md`](./CONTRIBUTING.md).

A build also runs once a night, without anybody pushing. The workflow *Nightly against the platform
snapshot* (`.github/workflows/nightly-platform-snapshot.yaml`) builds what a pull request builds,
with `--update-snapshots`. It builds against whatever the platform published since yesterday. A red
night opens an issue labelled `nightly-platform-snapshot`. Without it, a platform snapshot can break
this repository and nobody sees it until the next pull request. That happened on the 26th of
September 2026. The platform published at 12:19, the last build of the Camunda 7 adapter had run at
10:15, and the break showed up a day and a half later in somebody's pull request. The night writes
down which platform snapshot it used, with the timestamp and the build number. So you can tell
whether a red night is a break of the platform or of this repository.
