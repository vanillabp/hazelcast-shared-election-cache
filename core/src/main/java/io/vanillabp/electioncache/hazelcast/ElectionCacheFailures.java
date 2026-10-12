package io.vanillabp.electioncache.hazelcast;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What a failing cache logs, and how often. When Hazelcast does not answer, every call of the
 * cache answers as if the cache were empty (see decision 2 in the repository's DECISIONS.md). A
 * cluster which lost its Hazelcast fails every call. One log line per call would then hide the
 * cause under its symptoms.
 * <p>
 * So the first failure of an interval is logged with its exception. The failures after it are
 * only counted, and the next line says how many there were. The count tells an operator what
 * happened: a short hiccup during a rolling restart, or a cache which has been gone for an hour.
 */
public class ElectionCacheFailures {

  private static final Logger log = LoggerFactory.getLogger(ElectionCacheFailures.class);

  private final Duration interval;

  private final AtomicLong suppressedSinceLastLine = new AtomicLong();

  /**
   * When the last line was written, read from a monotonic clock. A wall clock would jump when
   * somebody changes the time.
   */
  private final AtomicLong lastLineNanos = new AtomicLong(Long.MIN_VALUE);

  /**
   * Starts with nothing suppressed and no line written yet. So the first failure in the life of
   * an application is always logged.
   *
   * @param interval How long a failure suppresses the lines of the failures behind it -
   *          the value of
   *          <code>vanillabp.workflow-adapter-cache.hazelcast.failure-log-interval</code>
   */
  public ElectionCacheFailures(
      final Duration interval) {

    this.interval = interval;

  }

  /**
   * Reports that an operation of the cache did not reach Hazelcast.
   *
   * @param operation What was attempted, as the reader of the log knows it
   *          (<code>"read a hint"</code>)
   * @param cause What Hazelcast threw
   */
  public void failed(
      final String operation,
      final Exception cause) {

    final var now = System.nanoTime();
    final var last = lastLineNanos.get();
    final var due = (last == Long.MIN_VALUE) || ((now - last) >= interval.toNanos());

    if (!due || !lastLineNanos.compareAndSet(last, now)) {
      suppressedSinceLastLine.incrementAndGet();
      return;
    }

    final var suppressed = suppressedSinceLastLine.getAndSet(0);

    log
        .warn(
            ElectionCacheMessages.cacheFailed(operation, suppressed, interval),
            cause);

  }

  /**
   * How many failures were counted without a line of their own since the last line. The tests
   * read it, and the next line reports it.
   *
   * @return The number of failures not logged so far
   */
  long suppressedSinceLastLine() {

    return suppressedSinceLastLine.get();

  }

}
