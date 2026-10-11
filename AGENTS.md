# Working on hazelcast-shared-election-cache

This repository implements VanillaBP's `WorkflowAdapterCache` on Spring Boot and on Quarkus. With
it, the nodes of one application share their BPMS elections.

Read [`README.md`](./README.md) first. The platform describes what the cache is for and what an
empty cache costs. It does so on the wiki page
[running more than one node](https://github.com/vanillabp/adapter-platform-integration/wiki/Running-more-than-one-node)
and in the javadoc of `io.vanillabp.integration.spi.WorkflowAdapterCache`. That javadoc is the
contract which this repository implements. Read it before every change here.

## The decision log is binding

[`DECISIONS.md`](./DECISIONS.md) holds the decisions which several places in this repository rely
on. It is the ONLY thing the code may cite. The citation has the plain form
`see decision 2 in the repository's DECISIONS.md`, so a grep finds it, and it names only entries of
THIS repository.

Read it before you change behaviour. An entry is not background reading. It is the reason why the
code around it looks the way it does. So a change which contradicts an entry is wrong until the
entry says otherwise.

**A decision is changed or replaced only after asking.** If your change would make an entry untrue,
stop and ask the maintainer before you write the change. If the answer is yes, the same commit
updates the log. The old entry STAYS. It is marked as superseded and names the entry which replaced
it. The new decision takes the next free number. Numbers are never reused and never renumbered,
because a citation in an older release still points at them.

The same rule holds for adding an entry. A decision gets a number when several places rely on it
and a copy of the explanation in each place would go stale. Anything smaller is a comment in the
place where it belongs. Anything larger is documentation.

## How we write

Most people who read this repository read English as a second language, and so does the
maintainer. Long sentences, rare words and stacked nouns slow them down. Write so that
nobody has to read a sentence twice.

Short main sentences, one thought each. One subordinate clause is enough. Active voice.
The common word instead of the rare one: `use` instead of `leverage`, `about` instead of
`regarding`, `so` instead of `consequently`, `has` instead of `possesses`. A technical term
stays a technical term, but say what it means the first time it turns up, and write an
abbreviation out once. If a sentence trips you up when you read it aloud, rewrite it.

This holds for every English text here: the README files, `DECISIONS.md`, this file, the
Javadoc and comments which explain something, and the texts of commits and pull requests. It
holds for the [wiki](https://github.com/vanillabp/hazelcast-shared-election-cache/wiki) as well,
once the repository has one, because a wiki clone has no `AGENTS.md` of its own.

Nothing a program reads is renamed for the sake of language. Class and method names,
configuration keys, artifact coordinates and the headlines of decision log entries stay as
they are, because code, tests and other repositories point at them.

Before and after, from this repository. The first example comes from the introduction of this file:

> What the cache is for and what an empty one costs is described from the platform's side, in
> running more than one node and in the javadoc of `io.vanillabp.integration.spi.WorkflowAdapterCache`;
> that javadoc is the contract this repository implements, and it is worth reading before every
> change here.

becomes

> The platform describes what the cache is for and what an empty cache costs. It does so on the
> wiki page running more than one node and in the javadoc of
> `io.vanillabp.integration.spi.WorkflowAdapterCache`. That javadoc is the contract which this
> repository implements. Read it before every change here.

The second one comes from the rules further down in this file:

> A hint lives `vanillabp.workflow-adapter-cache.time-to-live`, the hint of a workflow which ended
> `.ended-time-to-live`, and both are read from the platform's own configuration rather than
> duplicated under this repository's section.

becomes

> A hint lives for `vanillabp.workflow-adapter-cache.time-to-live`. The hint of a workflow which
> ended lives for `.ended-time-to-live`. Both are read from the platform's own configuration, not
> copied into this repository's section.

## Before you open a pull request

A number your branch hands out can be taken by the time you open the pull request. Another branch
was open at the same time and got there first. So check your numbers against `origin/main` and
against every open pull request, before the pull request exists.

It went wrong twice on 2026-09-13, in the Business Cockpit repository and not in this one. Two
branches claimed the same number, and they had to become 19 and 20. Two more claimed the next one,
and they had to become 21 and 22. Both times it showed up at the merge, which is the worst moment.
A merge happens on GitHub, and nobody can change a `see decision 21` in a Java file there.

The check:

```bash
bin/check-decision-numbers.sh
```

The script reports and changes nothing. By hand it is:

```bash
git fetch origin
git show origin/main:DECISIONS.md | grep -E '^### [0-9]+\. '  # the numbers already taken
gh pr list --state open
gh pr diff <n> | grep -E '^\+### [0-9]+\. '                   # for each open pull request
```

The entries of this repository are `###` headings. `gh pr diff` takes no path argument, so the
grep does the filtering.

If your number is taken, your entry gets the next free one. Then correct every citation of it in
the code and in the documentation.

Read each citation before you change it. Not every `see decision <n>` in the branch is about your
decision. A branch can cite a number which somebody else handed out long ago, and that citation
stays as it is. A search and replace over the whole branch turns a right reference into a wrong
one.

This does not break the rule that a number is never renumbered. That rule is about a merged number,
because a citation in a released artifact points at it. Until the pull request is merged, nothing
outside the branch has seen the number. So correcting it costs nothing beyond the branch.

Every other running number is checked the same way. The story prompts are such a series. They are
kept outside this repository, so they are checked where they are kept.

After you renumber, run `bin/check-decision-citations.sh`. It says whether every citation of a
decision still points at an entry. It also finds a citation which is wrapped over two lines. The
*Checks* workflow runs it on every pull request.

`bin/check-orphaned-javadoc.sh` finds a javadoc block which stands directly in front of a second
one. Javadoc drops such a block without a word, so its text appears nowhere. Run the script when
you wrote or moved a comment. If it reports a block, move the block back to the element it
describes. Do not delete it.

## Two rules which are easy to break here

**The two lifetimes are the platform's properties.** A hint lives for
`vanillabp.workflow-adapter-cache.time-to-live`. The hint of a workflow which ended lives for
`.ended-time-to-live`. Both are read from the platform's own configuration, not copied into this
repository's section. A team which moves from the in-memory default to this cache should find
every property it already set.

**Nothing may cost an operation.** A test which asserts that a method throws asserts the opposite
of what this repository promises. `UnreachableHazelcastTest` is the test which holds that promise.

## What code may point at

Code may not point at anything which a later change can make wrong without anybody noticing. So no
story or prompt number, no issue or pull request number, no chat transcript and no person. These
record a conversation at one point in time. A decision entry lives next to the code and is updated
in the same commit as the code. That is why code may cite it.

Where a name can carry the reason, the better fix is the name. Where it cannot, a comment says why
in its own words, complete where it stands. Only what several places have to carry becomes an
entry in the log.

Commit messages and pull request descriptions may cite whatever they like. They record one point
in time themselves.
