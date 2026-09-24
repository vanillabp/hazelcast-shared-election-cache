# Contributing

This repository holds one implementation of VanillaBP's `WorkflowAdapterCache`: the nodes of an
application form a small Hazelcast cluster among themselves and share which BPMS holds which
workflow. It is not a BPMS adapter and it carries no business rules. Everything it does for the
people using it is in [`README.md`](./README.md), which is the only documentation here, because
there is not enough of it to justify a wiki. The concept behind the cache belongs to the platform
and is documented
[there](https://github.com/vanillabp/adapter-platform-integration/wiki/BPMS-migration#the-election-cache).

Where the rules are: [`AGENTS.md`](./AGENTS.md) says how work is done here, in the form an agent
reads, and [`DECISIONS.md`](./DECISIONS.md) holds the decisions several places rely on, which is the
only thing the code is allowed to cite.

## Building and testing

A JDK 21 or newer, and Maven, without a wrapper. The workflows build with the JDK named in
`.github/workflows`, currently 25, so build with that one if you want to see what the pipeline sees.
The class files stay at Java 21 either way, because that is what the property `version.java` in the
root `pom.xml` compiles against:

```bash
mvn spotless:apply
mvn install
```

`install` and not `install verify`: `install` already runs every phase `verify` has, and the Quarkus
tests load their modules from the local Maven repository, so `package` leaves them with the module
of the run before. Spotless formats Markdown too.

Two tools read the javadoc, and each one sees a part the other misses. The compiler checks every
class for a broken reference or broken HTML, the package private ones included. The javadoc plugin
checks what the published documentation shows, so it starts at protected and stops there. One thing
below protected is shown as well: the fields a serializable class carries into its serialized form,
which is why a private field of an exception is asked for a comment too.

A comment which is missing breaks the build. Everything this repository publishes has one now, and
the plugin fails on a warning so that it stays that way. Write the sentence rather than switching
the check off, and write the one a reader needs: what this repository publishes is read by somebody
wiring it into an application, and `@return the value` is the same gap in a longer form. A module
which publishes nothing sets `maven.javadoc.skip`, so a test module is never asked for comments.

One thing the javadoc plugin cannot see is what Lombok generates, because it reads the source and
Lombok writes bytecode. So a published comment names a property in words rather than linking a
getter which is not in the file, and a published class which takes its constructor from Lombok
writes that constructor out, because the documentation otherwise shows a parameterless one which
does not exist.

Two javadoc blocks in a row are the gap neither tool sees. Javadoc keeps the last block before an
element and drops the earlier ones without a word, so a comment somebody wrote and kept up to date
appears nowhere. `bin/check-orphaned-javadoc.sh` finds that shape. A block it reports describes
something, usually the element next door, so hang it back there rather than delete it.

Nothing here needs Docker or a network. Two Hazelcast members in one JVM are the cheapest cluster
there is and that is what the tests use: one node writes a hint, another one reads it, and the
election of the platform runs across both. The tests boot a real application, and VanillaBP refuses
to start without a BPMS adapter, so the
[Process-Engine-API adapter](https://github.com/vanillabp/process-engine-api-adapter) is on the test
classpath as a double. Nothing about this cache depends on which BPMS an application uses.

A hint is a hint. Losing one costs a walk through the adapters and never correctness, so the test
which takes the cache away is as important as the ones which use it. Keep it that way.

## What a POM hands an application

A tool which only translates our source belongs in scope `provided`, and the scope stands at the
declaration in the module which uses the tool. Lombok is such a tool, an annotation processor is
another. An application asked for a workflow engine, and every jar it did not ask for is one more
thing to ship and to answer a CVE report about.

Writing `<optional>true</optional>` in a `dependencyManagement` does not do it. Maven copies a
managed version, scope and exclusions into a dependency and leaves the optional flag behind, so the
POM we publish says nothing at all about that dependency. Lombok reached the runtime classpath of
every application that way, here and in the platform.

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

## What is asked before the code is written

Where a change would make an entry of [`DECISIONS.md`](./DECISIONS.md) untrue, ask before you write
it and wait for the answer. An entry is never edited away: it stays, marked as superseded and naming
its successor, and the new decision takes the next free number.

The second question is the interface. `WorkflowAdapterCache` is published by the platform
integration and an application may bring its own implementation, so what the cache promises is
decided there. A property this repository owns, such as how many backups a hint gets, is decided
here.

## Opening a pull request

Work on a branch of your own and keep one subject per pull request. The description says what moved
and why it had to. It may cite an issue or a conversation, because it is a record of a moment itself,
which the code is not.

Check the numbers your branch hands out before you open it. Another branch may have taken the
decision number you used while you were writing, and once a pull request is merged a
`see decision 2` in a Java file can no longer be corrected on GitHub:

```bash
bin/check-decision-numbers.sh
```

The *Publish to GitHub Packages* workflow builds and tests every pull request and publishes nothing
from a branch. A red check is a finding about your change. Read the log and fix what it says rather
than pushing again to see whether it goes away. Releases are a second workflow, started by hand with
the version to release, and it refuses to run while a POM still names a SNAPSHOT.

## License

VanillaBP is published under the [Apache License, Version 2.0](./LICENSE), and by contributing you
agree that your contribution is licensed the same way. [`NOTICE`](./NOTICE) names who holds the
copyright.
