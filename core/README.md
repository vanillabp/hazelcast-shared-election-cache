# The cache itself

Plain Java: the `WorkflowAdapterCache` of the integration SPI on top of a Hazelcast map, plus the
member that map lives in. No Spring, no CDI, and no BPMS adapter. The two platform modules only bind
the configuration and create beans. So everything worth reading about this cache is in this
module.

## What a hint is filed under

The platform hands over three strings, and `ElectionCacheKey` joins them with `|`. They are the
workflow module, the BPMN process and the workflow-aggregate ID, in the serialized form the outbox
uses. The aggregate ID comes last, because it is the only part which can contain any character. A
BPMN process ID is an XML name, and a workflow module ID is a configuration key. A key longer than
512 characters is stored as a digest (a hash) of itself. The platform limits the keys it stores the
same way. Nobody ever reads this key back for a human, so the digest loses nothing.

The key is the only thing two nodes have to agree on without talking about it. Think of a rolling
restart, where old and new versions run side by side. If the separator or the 512 limit changed, a
node on the old version could not see the hints of a node on the new one. That is why
`ElectionCacheKeyTest` pins the shape of the key, not its behaviour.

## The two lifetimes are per entry

One map holds the hints of living workflows and the marks of ended ones. A time-to-live for the
whole map could not tell them apart. So `put` carries
`vanillabp.workflow-adapter-cache.time-to-live`, `putEnded` carries `.ended-time-to-live`, and
Hazelcast expires each entry on its own.

`putEnded` has a rule which needs to read the entry and write it in one step. It leaves a hint which
names ANOTHER adapter alone. The key names the aggregate and not the workflow instance. So such a
hint can only come from the election of a second workflow on the same aggregate. Reading and then
writing back would be two calls with a race between them. That is why `MarkEnded` is an entry
processor. Hazelcast runs it on the member which owns the key and holds that key while it runs. The
class travels to that member, and that member is a node of this application.

## Nothing may cost an operation

Every method catches what Hazelcast can throw and answers like an empty cache. If a member cannot
start at all, the platform modules handle it one level higher: they use VanillaBP's in-memory cache
and log a warning. Both are decision 2 in the repository's `DECISIONS.md`, and
`UnreachableHazelcastTest` checks the promise.

When a node's cluster is gone, `ElectionCacheFailures` stops it from writing a log line for each
call. It logs the first failure of an interval with its cause and counts the ones after it. So the
next line says how long this has been going on.

## What the log has to make visible

A member which finds nobody forms a cluster of one, and nothing looks wrong. That is why
`ClusterVisibility` exists. It states the size of the cluster at startup. It logs every change of
members with the new size. And it warns for as long as this member is alone. The sentences
themselves live in `ElectionCacheMessages`, one place for both platform modules. Each of them is
meant to end an operator's search, not just to record an event.

## Where the member's configuration comes from

`ElectionCacheMemberConfig` builds the Hazelcast configuration from
`HazelcastElectionCacheProperties` and from nothing else. It reads no `hazelcast.xml`. An
application may have put such a file on the classpath for its own Hazelcast, and that file cannot
quietly change the cluster the election cache joins. Exactly one way of finding members is switched
on. So every branch switches the other three off. Hazelcast refuses a configuration with two of
them, and its auto-detection stays on until something else is switched on.

The member is a cache, not a Hazelcast installation. The stream engine is off. The telemetry which
Hazelcast sends to its vendor is off. The only map configured is the one which holds the hints. An
application which wants more than that hands this cache its own instance instead.
