# Decision log

Decisions this repository's code points at. A number is handed out once and never reused or
renumbered, so a citation stays resolvable; a decision which gets overturned keeps its entry,
marked as superseded and naming the entry which replaced it.

A citation in code reads `see decision 2 in the repository's DECISIONS.md`, and it names an entry
of THIS repository only. A decision which the platform shares has its own entry in
`adapter-platform-integration`, written from that side; a pointer into another repository is the
fragile kind this log exists to avoid.

Links below point into this repository's [`README.md`](./README.md), which carries the detail an
entry deliberately leaves out.

### 1. The nodes of the application are the cluster

The hints live in Hazelcast members embedded in the application's own nodes, not in a Hazelcast
cluster somebody operates next to it. The reason this cache is shipped at all is that a team going
from one node to two should not have to take on a component it did not have before, and a client
against a cluster of its own would hand that team exactly the operational work the in-memory
default was trying to save them.

The price is that forming the cluster becomes part of the deployment, and it is a real price. A
member which finds nobody forms a cluster of one and nothing looks wrong: every node then has its
private cache again, the application behaves exactly as it did before, and the problem which
brought the team here is still there. That is why the size of the cluster is stated at startup,
why every membership change is logged with the size after it, and why a member which is alone
repeats that warning for as long as it lasts. Discovery is therefore part of this repository's
design rather than a sentence in its README, and it offers all four ways a deployment can offer
one: multicast, a list of a few members, a headless Kubernetes service resolved through DNS, and
the endpoints of a Kubernetes service read through its API.

A client against a foreign cluster stays possible, as a module of its own for installations which
already run Hazelcast, and never as a switch inside this one: the two have different failure
modes, different things to operate and different documentation. What this repository does support
is moving into an instance the application already provides, which is the same JVM and therefore
still no new component.

### 2. Every method answers as if the cache were empty rather than throwing

An entry of this cache is a hint. The platform probes the adapter a hint names and repairs the
entry where the hint proved wrong, so a wrong or missing hint costs one probing walk and never a
wrong result. That is what makes it safe to keep the hints in infrastructure which can be away -
and it only holds as long as this implementation never turns an absent hint into an exception.

So every method catches what the Hazelcast API can throw and answers like an empty cache: `get`
returns nothing, `put`, `putEnded` and `invalidate` return quietly. A member which cannot be
started at all is answered the same way, one level higher: the application boots with the
in-memory cache of the platform, per node, and a warning says what is degraded. Nothing here ever
ends a boot because Hazelcast is away, and nothing makes an operation fail which would have
succeeded without a cache.

This will look wrong to somebody later, because it hides an infrastructure failure from the
operation of the application on purpose. The log is where such a failure surfaces, and the message
says both halves of it: what is degraded, and that workflow operations are unaffected. It is
reported once per interval rather than once per call, because a cluster whose Hazelcast is gone
fails every call and a line per call would bury the incident under its symptom.
