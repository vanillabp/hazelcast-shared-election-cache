# The cache itself

Plain Java: the `WorkflowAdapterCache` of the integration SPI against a Hazelcast map, plus the
member that map lives in. No Spring, no CDI, and no BPMS adapter. The two platform modules bind
configuration and create beans, nothing else, which is why everything worth reading about this
cache is in this module.

## What a hint is filed under

The platform hands over three strings, and `ElectionCacheKey` joins them with `|`: the workflow
module, the BPMN process and the workflow-aggregate ID in the serialized form the outbox uses. The
aggregate ID comes last because it is the only part which can contain anything, while a BPMN process
ID is an XML name and a workflow module ID is a configuration key. Beyond 512 characters the key is
stored as a digest of itself, the way the platform bounds the keys it persists, since nothing ever
reads this key back for a human.

The key is the only thing two nodes have to agree on without talking about it. A changed separator
or a changed boundary would make the hints of a node running the new version invisible to a node
running the old one during a rolling restart, which is why `ElectionCacheKeyTest` pins the shape
rather than the behaviour.

## The two lifetimes are per entry

One map holds the hints of living workflows and the marks of ended ones, so a map-wide
time-to-live could not tell them apart. `put` carries
`vanillabp.workflow-adapter-cache.time-to-live`, `putEnded` carries `.ended-time-to-live`, and
Hazelcast expires each entry on its own.

`putEnded` has a rule which needs the entry and the write in one step: a hint naming ANOTHER adapter
is left alone, because the key names the aggregate rather than the workflow instance, so such a hint
can only have been written by the election of a second workflow on the same aggregate. Reading and
writing back would be two calls with a race between them, which is why `MarkEnded` is an entry
processor: Hazelcast runs it on the member owning the key and holds that key while it does. The
class travels to that member, and that member is a node of this application.

## Nothing may cost an operation

Every method catches what Hazelcast can throw and answers like an empty cache. A member which cannot
be started at all is answered one level higher, in the platform modules, with VanillaBP's in-memory
cache and a warning. Both are decision 2 in the repository's `DECISIONS.md`, and
`UnreachableHazelcastTest` is where the promise lives.

`ElectionCacheFailures` is what keeps a node whose cluster is gone from writing a log line per call:
the first failure of an interval is logged with its cause and the ones behind it are counted, so the
next line says how long this has been going on.

## What the log has to make visible

`ClusterVisibility` exists because a member which finds nobody forms a cluster of one and nothing
looks wrong. It states the size of the cluster at startup, logs every membership change with the
size after it, and warns for as long as this member is alone. The sentences themselves live in
`ElectionCacheMessages`, one place for both platform modules, because each of them is meant to end
an operator's search rather than to record an event.

## Where the member's configuration comes from

`ElectionCacheMemberConfig` builds the Hazelcast configuration from
`HazelcastElectionCacheProperties` and from nothing else. No `hazelcast.xml` is read, so a file an
application put on the classpath for its own Hazelcast cannot silently change the cluster the
election cache joins. Exactly one way of finding members is enabled, which is why every branch
switches the other three off: Hazelcast refuses a configuration with two of them, and its
auto-detection is on until something else is.

The member is a cache and not a Hazelcast installation. The stream engine is off, the telemetry
Hazelcast sends to its vendor is off, and the only map configured is the one holding the hints. An
application which wants more than that gives us its own instance instead.
