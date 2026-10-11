# Contributing

This repository holds one implementation of VanillaBP's `WorkflowAdapterCache`. The nodes of an
application form a small Hazelcast cluster among themselves and share which BPMS holds which
workflow. It is not a BPMS adapter, and it has no business rules. Everything it does for its users
is in [`README.md`](./README.md). That is the only documentation here, because there is too little
to fill a wiki. The idea behind the cache belongs to the platform, and the platform explains it
[there](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-migration#the-election-cache).

Where the rules are: [`AGENTS.md`](./AGENTS.md) says how work is done here, in the form an agent
reads. [`DECISIONS.md`](./DECISIONS.md) holds the decisions several places rely on. It is the only
thing the code may cite.

## From a bug to a pull request

Found a bug? You can hand it to a coding agent.

1. Clone [`development-workspace`](https://github.com/vanillabp/development-workspace) with
   `git clone --recurse-submodules`. It holds the VanillaBP repositories side by side. Its
   `.claude/skills` folder holds the skills Claude Code reads when it starts at the root of the
   workspace.
2. Start your coding agent at the root of the workspace and describe the bug: what you did, what
   you expected and what happened instead.
3. The agent builds a scenario which reproduces the bug, looks for the cause and fixes it. Then it
   pushes a branch to your fork and opens a pull request from there.
4. The VanillaBP team reviews the pull request and merges it.

You need a GitHub account, the GitHub CLI logged in with `gh auth login`, a fork of the repository
you change (the agent can create it with `gh repo fork`), and Java 21, Maven and Docker for the
build. The submodules are cloned over SSH. If GitHub has no SSH key of yours, run
`git config --global url."https://github.com/".insteadOf "git@github.com:"` before you clone. If
the bug is in a repository the workspace does not hold, the agent clones it next to the others.
Before it opens the pull request, the agent follows the `CONTRIBUTING.md` and the `AGENTS.md` of
the repository it changes, where it has one. The two Camunda adapters live in the Camunda Community
Hub, which asks you to sign its contributor license agreement on your first pull request there.

## Building and testing

You need a JDK 21 or newer, and Maven without a wrapper. The workflows build with the JDK named in
`.github/workflows`, currently 25. Build with that one if you want to see what the pipeline sees.
The class files stay at Java 21 either way, because the property `version.java` in the root
`pom.xml` compiles for Java 21:

```bash
mvn spotless:apply
mvn install
```

`install` and not `install verify`: `install` already runs every phase of `verify`. The Quarkus
tests load their modules from the local Maven repository, so after `package` they would test the
module of the run before. Spotless formats Markdown too.

Two tools read the javadoc, and each one sees a part the other misses. The compiler checks every
class for a broken reference or broken HTML, the package private ones included. The javadoc plugin
checks what the published documentation shows, so it looks at protected and public only. One thing
below protected is shown as well: the fields a serializable class carries into its serialized form.
That is why a private field of an exception needs a comment too.

A missing comment breaks the build. Everything this repository publishes has one now, and the
plugin fails on a warning so that it stays that way. Write the sentence instead of switching the
check off. And write the one a reader needs. The reader is somebody who wires this repository into
an application, and `@return the value` leaves the same gap, only in more words. A module which
publishes nothing sets `maven.javadoc.skip`, so a test module never needs comments.

The javadoc plugin cannot see what Lombok generates, because it reads the source and Lombok writes
bytecode. So a published comment names a property in words. It does not link a getter which is not
in the file. A published class which gets its constructor from Lombok writes that constructor out.
Otherwise the documentation shows a constructor without parameters which does not exist.

Two javadoc blocks in a row are the gap neither tool sees. Javadoc keeps the last block before an
element and silently drops the earlier ones. So a comment somebody wrote and kept up to date appears
nowhere. `bin/check-orphaned-javadoc.sh` finds that shape. A block it reports describes something,
usually the element next to it. Move it back there instead of deleting it.

Nothing here needs Docker or a network. Two Hazelcast members in one JVM are the cheapest cluster
there is, and the tests use exactly that. One node writes a hint, another one reads it, and the
election of the platform runs across both. The tests boot a real application, and VanillaBP refuses
to start without a BPMS adapter. So the
[Process-Engine-API adapter](https://github.com/vanillabp/process-engine-api-adapter) is on the test
classpath as a stand-in. Nothing about this cache depends on which BPMS an application uses.

A hint is only a hint. Losing one costs a walk through the adapters and never a wrong result. So the
test which takes the cache away matters as much as the ones which use it. Keep it that way.

## What a POM hands an application

A tool which only translates our source belongs in scope `provided`. Write the scope at the
dependency in the module which uses the tool. Lombok is such a tool, and so is an annotation
processor. An application asked for a workflow engine. Every jar it did not ask for is one more
thing to ship, and one more thing to answer a CVE report (a published security issue) about.

`<optional>true</optional>` in a `dependencyManagement` does not work. Maven copies a managed
version, scope and exclusions into a dependency, but not the optional flag. So the POM we publish
says nothing at all about that dependency. That is how Lombok reached the runtime classpath of every
application, here and in the platform.

## A POM comment says what this POM does

A comment in a POM says what this POM sets, and why that differs from what it would get anyway. It
does not repeat what the parent sets. `io.vanillabp:release-parent` is released on its own schedule.
So a copy of its settings in a child here goes out of date, and nothing notices. The comments of
three POMs in this organisation did exactly that. They said the parent switched the javadoc check
off, after a release of the parent had switched it on.

Sometimes a module really does rely on something its parent does or does not do. Then say so as an
assumption, and name the version you checked it against. The next reader can check it again with one
command, `mvn help:effective-pom`, instead of believing a sentence.

## How we write

Most people who read this repository read English as a second language, and so does the maintainer.
Long sentences, rare words and stacked nouns slow them down. Write so that nobody has to read a
sentence twice.

Short main sentences, one thought each. One subordinate clause is enough. Active voice. The common
word instead of the rare one: `use` instead of `leverage`, `about` instead of `regarding`, `so`
instead of `consequently`. A technical term stays a technical term, but say what it means the first
time it turns up, and write an abbreviation out once. If a sentence trips you up when you read it
aloud, rewrite it.

This holds for every English text here, the javadoc, the commit message and the pull request
included. Nothing a program reads is renamed for the sake of language: type and method names,
configuration keys and artifact coordinates stay as they are, because code in other repositories
points at them.

[`AGENTS.md`](./AGENTS.md) has the same rule with examples from this repository, before and after.

## What is asked before the code is written

If a change would make an entry of [`DECISIONS.md`](./DECISIONS.md) untrue, ask before you write it.
Then wait for the answer. An entry is never edited away. It stays, marked as superseded and naming
the entry which replaced it, and the new decision takes the next free number.

The second question is the interface. The platform integration publishes `WorkflowAdapterCache`,
and an application may bring its own implementation. So the platform decides what the cache
promises. A property this repository owns, such as how many backups a hint gets, is decided here.

## Opening a pull request

Work on a branch of your own, and keep to one subject per pull request. The description says what
changed and why. It may cite an issue or a conversation. A description records one moment, and the
code does not.

Check the numbers your branch hands out before you open the pull request. While you were writing,
another branch may have taken the decision number you used. Once a pull request is merged, a
`see decision 2` in a Java file can no longer be corrected on GitHub:

```bash
bin/check-decision-numbers.sh
```

After you corrected a number, `bin/check-decision-citations.sh` tells you whether every citation of
a decision still points at an entry of `DECISIONS.md`. The *Checks* workflow runs it on every pull
request.

The *Build and publish snapshots* workflow builds and tests every pull request. It publishes nothing
from a branch. A red check tells you something about your change. Read the log and fix what it says.
Do not just push again to see whether it goes away. Releases have a second workflow. You start it
by hand with the version to release, and it refuses to run while a POM still names a SNAPSHOT.

A pull request from a fork builds and tests like any other, because the snapshots it needs can be
read without a login. It cannot publish anything, because GitHub gives it no secrets of this
repository.

`main` has a ruleset which requires two green checks before a merge. One is `publish`, the build of
*Build and publish snapshots*. The other is `orphaned-javadoc-check`, the *Checks* workflow which
runs the script named above. While one of them is red, GitHub does not offer the merge.

## License

VanillaBP is published under the [Apache License, Version 2.0](./LICENSE). By contributing, you agree
that your contribution is licensed the same way. [`NOTICE`](./NOTICE) names who holds the
copyright.
