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
