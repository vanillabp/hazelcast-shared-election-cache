package io.vanillabp.electioncache.hazelcast;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What a failing cache says, and how often. Every call of the cache answers as if it
 * were empty when Hazelcast does not answer (see decision 2 in the repository's
 * DECISIONS.md), and a cluster which lost its Hazelcast fails every call: one log line
 * per call would then bury the incident which caused it under the symptom.
 * <p>
 * So the first failure of an interval is logged with its exception and the ones behind
 * it are counted, and the next line says how many there were. The count is what tells
 * an operator whether this was a hiccup during a rolling restart or a cache which has
 * been gone for an hour.
 */
public class ElectionCacheFailures {

  private static final Logger log = LoggerFactory.getLogger(ElectionCacheFailures.class);

  private final Duration interval;

  private final AtomicLong suppressedSinceLastLine = new AtomicLong();

  /**
   * When the last line was written, as a monotonic reading - never a wall clock, which
   * a time change would move.
   */
  private final AtomicLong lastLineNanos = new AtomicLong(Long.MIN_VALUE);

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
   * How many failures have been counted without a line of their own since the last
   * one - read by the tests, and the number the next line reports.
   *
   * @return The number of failures not logged so far
   */
  long suppressedSinceLastLine() {

    return suppressedSinceLastLine.get();

  }

}
