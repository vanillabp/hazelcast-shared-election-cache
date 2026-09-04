# Working on hazelcast-shared-election-cache

The implementation of VanillaBP's `WorkflowAdapterCache` which lets the nodes of one application
share their BPMS elections, on Spring Boot and on Quarkus.

Read [`README.md`](./README.md) first. What the cache is for and what an empty one costs is
described from the platform's side, in
[running more than one node](https://github.com/vanillabp/adapter-platform-integration/wiki/Running-more-than-one-node)
and in the javadoc of `io.vanillabp.integration.spi.WorkflowAdapterCache`; that javadoc is the
contract this repository implements, and it is worth reading before every change here.

## The decision log is binding

[`DECISIONS.md`](./DECISIONS.md) holds the decisions several places in this repository rely on. It
is the ONLY thing the code is allowed to cite, in the plain greppable form
`see decision 2 in the repository's DECISIONS.md`, and only entries of THIS repository.

Read it before you change behaviour. An entry is not background reading, it is the reason the code
around it looks the way it does, so a change which contradicts one is wrong until the entry says
otherwise.

**A decision is changed or replaced only after asking.** Where your change would make an entry
untrue, stop and put the question to the maintainer before you write the change. If the answer is
yes, the same commit updates the log: the old entry STAYS, marked as superseded and naming the
entry which replaced it, and the new decision takes the next free number. Numbers are never reused
and never renumbered, because a citation in an older release still points at them.

Adding an entry has the same rule. A decision earns a number when several places rely on it and
copying the explanation to each of them would rot; anything smaller is a comment where it belongs,
and anything larger is documentation.

## Two rules which are easy to break here

**The two lifetimes are the platform's properties.** A hint lives
`vanillabp.workflow-adapter-cache.time-to-live`, the hint of a workflow which ended
`.ended-time-to-live`, and both are read from the platform's own configuration rather than
duplicated under this repository's section. A team moving from the in-memory default to this cache
should recognise every property it already set.

**Nothing may cost an operation.** A test which asserts that a method throws is asserting the
opposite of what this repository promises. `UnreachableHazelcastTest` is where that promise lives.

## What code may point at

Nothing which a later change can invalidate without anything noticing: no story or prompt number,
no issue or pull-request number, no chat transcript, no person. Those record a conversation at a
point in time. A decision entry lives next to the code and is overhauled in the same commit, which
is what makes it citable.

Where a name can carry the reason, the name is the better fix. Where it cannot, a comment says why
in its own words, complete where it stands. Only what several places have to carry becomes an
entry in the log.

Commit messages and pull-request descriptions may cite whatever they like. They are records of a
point in time themselves.
