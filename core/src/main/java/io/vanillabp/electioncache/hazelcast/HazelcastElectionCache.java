package io.vanillabp.electioncache.hazelcast;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import io.vanillabp.integration.spi.WorkflowAdapterCache;

/**
 * The election cache of a cluster. It keeps the hint "this adapter holds the workflow of this
 * aggregate" in a Hazelcast map, which every node of the application reads and writes.
 * <p>
 * A node which did not start a workflow, and never got a delivery of it, knows nothing about
 * it. So its election asks every adapter in turn. A BPMS may answer from a read model which is
 * not up to date yet. The election waits for the workflow to show up there only if it has a note
 * that this application started the workflow on that BPMS. With this cache, that note belongs to
 * the whole cluster, not to one node.
 * <p>
 * Hazelcast may be gone, and two things make that safe. An entry is only a hint. And every method
 * of this class answers as if the cache were empty when Hazelcast does not answer (see decision 2
 * in the repository's DECISIONS.md). A wrong or missing hint costs one round of asking the
 * adapters. A cache which throws would make the operation fail.
 */
public class HazelcastElectionCache implements WorkflowAdapterCache {

  private final IMap<String, String> hints;

  private final long timeToLiveMillis;

  private final long endedTimeToLiveMillis;

  private final ElectionCacheFailures failures;

  /**
   * Takes the map, and turns the two lifetimes into milliseconds once. Hazelcast gets the
   * lifetime of an entry with every write, not once for the map. The map holds the hints of
   * running workflows and of ended ones side by side.
   *
   * @param instance The Hazelcast member the hints live in
   * @param mapName The name of the map holding the hints
   * @param timeToLive How long the hint of a living workflow is kept - the value of
   *          <code>vanillabp.workflow-adapter-cache.time-to-live</code>, so that moving
   *          from the in-memory default to this cache changes no behaviour
   * @param endedTimeToLive How long the hint of a workflow which ended is kept
   * @param failures Where a Hazelcast which does not answer is reported
   */
  public HazelcastElectionCache(
      final HazelcastInstance instance,
      final String mapName,
      final Duration timeToLive,
      final Duration endedTimeToLive,
      final ElectionCacheFailures failures) {

    this.hints = instance.getMap(mapName);
    this.timeToLiveMillis = timeToLive.toMillis();
    this.endedTimeToLiveMillis = endedTimeToLive.toMillis();
    this.failures = failures;

  }

  @Override
  public Optional<String> get(
      final String workflowModuleId,
      final String bpmnProcessId,
      final String workflowAggregateId) {

    try {
      return Optional
          .ofNullable(
              hints.get(ElectionCacheKey.of(workflowModuleId, bpmnProcessId, workflowAggregateId)));
    } catch (final Exception e) {
      failures.failed("read the hint of a workflow", e);
      return Optional.empty();
    }

  }

  @Override
  public void put(
      final String workflowModuleId,
      final String bpmnProcessId,
      final String workflowAggregateId,
      final String adapterId) {

    try {
      hints
          .put(
              ElectionCacheKey.of(workflowModuleId, bpmnProcessId, workflowAggregateId),
              adapterId,
              timeToLiveMillis,
              TimeUnit.MILLISECONDS);
    } catch (final Exception e) {
      failures.failed("store the hint of a workflow", e);
    }

  }

  @Override
  public void putEnded(
      final String workflowModuleId,
      final String bpmnProcessId,
      final String workflowAggregateId,
      final String adapterId) {

    try {
      hints
          .executeOnKey(
              ElectionCacheKey.of(workflowModuleId, bpmnProcessId, workflowAggregateId),
              new MarkEnded(adapterId, endedTimeToLiveMillis));
    } catch (final Exception e) {
      failures.failed("mark the hint of a workflow which ended", e);
    }

  }

  @Override
  public void invalidate(
      final String workflowModuleId,
      final String bpmnProcessId,
      final String workflowAggregateId) {

    try {
      // 'delete', not 'remove': nobody reads the value 'remove' returns, and fetching
      // it from the member which owns the key would be a second network call
      hints.delete(ElectionCacheKey.of(workflowModuleId, bpmnProcessId, workflowAggregateId));
    } catch (final Exception e) {
      failures.failed("drop the hint of a workflow", e);
    }

  }

}
