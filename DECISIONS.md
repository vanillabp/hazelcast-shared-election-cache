# Decision log

The decisions the code of this repository points at. Each number is handed out once. It is never
used again and never changed, so a citation always finds its entry. A decision which is overturned
keeps its entry. The entry is marked as superseded and names the entry which replaced it.

A citation in code reads `see decision 2 in the repository's DECISIONS.md`. It names an entry of
THIS repository only. A decision which the platform shares has its own entry in
`adapter-platform-integration`, written from the platform's side. A pointer into another repository
breaks easily, and this log exists to avoid exactly that.

The links below point into this repository's [`README.md`](./README.md). It has the details an entry
leaves out on purpose.

### 1. The nodes of the application are the cluster

The hints live in Hazelcast members inside the application's own nodes. They do not live in a
Hazelcast cluster which somebody operates next to the application. This cache exists for one reason.
A team which goes from one node to two should not have to run a component it did not have before. A
client of a separate cluster would give that team exactly the operations work which the in-memory
default was meant to save them.

The price is that forming the cluster becomes part of the deployment, and that price is real. A
member which finds nobody forms a cluster of one, and nothing looks wrong. Every node has its private
cache again. The application behaves exactly as before, and the problem which brought the team here
is still there. That is why the size of the cluster is stated at startup. Every change of members is
logged with the new size. And a member which is alone repeats that warning for as long as it is
alone. So discovery, the way nodes find each other, is part of this repository's design and not
just a sentence in its README. It supports all four ways a deployment can offer for this: multicast, a list of
a few members, a headless Kubernetes service resolved through DNS, and the endpoints of a Kubernetes
service read through its API.

A client of a separate cluster stays possible for installations which already run Hazelcast. It
would be a module of its own, never a switch inside this one. The two fail in different ways, need
different things operated and need different documentation. What this repository does support is
using an instance the application already provides. That is the same JVM, so it is still no new
component.

### 2. Every method answers as if the cache were empty rather than throwing

An entry of this cache is a hint. The platform asks the adapter which a hint names. Where the hint
was wrong, the platform repairs the entry. So a wrong or missing hint costs one probing walk and
never gives a wrong result. That is why the hints can live in infrastructure which may be away. It
only holds as long as this implementation never turns a missing hint into an exception.

So every method catches what the Hazelcast API can throw and answers like an empty cache. `get`
returns nothing. `put`, `putEnded` and `invalidate` return quietly. A member which cannot start at
all is handled the same way, one level higher. The application boots with the in-memory cache of
the platform, one per node, and a warning says what is degraded. Nothing here ever ends a boot
because Hazelcast is away. Nothing makes an operation fail which would have worked without a cache.

This will look wrong to somebody later. It hides an infrastructure failure from the application's
operations, and it does so on purpose. The failure shows up in the log. The message says two things:
what is degraded, and that workflow operations are not affected. It is reported once per interval,
not once per call. A cluster whose Hazelcast is gone fails on every call, and a line per call would
bury the real problem under its symptoms.

### 3. A snapshot reaches Maven Central again, without a sources jar, and a refused request is repeated

Proposed by stories 2162 and 2160, 2026-11-13. Story 2158 brought the profile `publish-snapshots`,
which sends a snapshot to GitHub Packages and to the snapshot repository of Maven Central in one
build. It wrote no decision here. `adapter-platform-integration` made the same change, see decision
139 in adapter-platform-integration for the URL and decision 140 in adapter-platform-integration for
the sources jar and the repeats.

**The snapshot reaches Maven Central again.** The profile `publish-snapshots` sets the snapshot
repository of the POM to GitHub Packages, because that is where `maven-deploy-plugin` uploads.
`central-publishing-maven-plugin` 0.12.0 reads the same setting: where its configuration names no
`centralSnapshotsUrl`, it uploads a snapshot to the snapshot repository of the POM. So since story
2158 every snapshot of this repository went to GitHub Packages twice and never to Maven Central, and
the build stayed green. The configuration of the plugin in the profile now names
`https://central.sonatype.com/repository/maven-snapshots/` as `centralSnapshotsUrl`. The modules
which are not published keep `skip.central.release`, so the list of published modules is the same as
before.

**A snapshot carries no sources jar.** Maven Central answers the GitHub runners with 429 Too Many
Requests, and every file a publish uploads is one more request. The root POM no longer binds
`maven-source-plugin`, so a snapshot carries the jar, the POM and their checksums, and nothing else.
GitHub Packages gets no sources jar either, because both repositories get their files from one
build. A release still carries a sources jar and a javadoc jar: the release workflow runs with the
profile `release` of `io.vanillabp:release-parent`, which binds both. A developer who opens a class
of a snapshot in the IDE sees decompiled code again, as before the root POM bound the plugin.
Modules which build no jar and publish nothing (`test-coverage-report/coverage-gate`) bind the
plugin themselves. Without a file of its own in a module, `central-publishing-maven-plugin` stops
the whole publish, also for a module it leaves out, and the sources jar is the one file such a
module has.

**A refused request is repeated.** `.mvn/maven.config` tells Maven to repeat a request which a
server answers with 429 or 503. It repeats a request at most twice and waits as long as the
`Retry-After` header asks, up to 35 minutes. Central blocks a runner for about half an hour, and
every request during the block makes it longer, so waiting once is better than asking again. Maven
also uploads one file after the other instead of five at once. The file applies to every build of
this repository, on a laptop and in a pull request as well.

**Where it is referred to:** the comments at `centralSnapshotsUrl` and `skipPublishing` in the
profile `publish-snapshots` of `pom.xml`, the comment in `pom.xml` where `maven-source-plugin` used
to be bound, the comment at `maven-source-plugin` in the POM of each module which builds no jar, and
the comments in `.mvn/maven.config`. None of them cites a number.
