package io.vanillabp.electioncache.hazelcast;

import io.vanillabp.integration.spi.StoredKey;

/**
 * The key of one hint, as the three strings the platform hands over: workflow module,
 * BPMN process and the workflow-aggregate ID in the serialized form the outbox uses.
 * <p>
 * The aggregate ID comes last because it is the only part which can contain anything -
 * a composite business key, a URN, whatever the domain model uses as an identity. The
 * two parts before it cannot contain the separator: a BPMN process ID is an XML name
 * and a workflow module ID is a configuration key.
 * <p>
 * The length is bounded the way the platform bounds the keys it persists
 * ({@link StoredKey}), and for the same reason as there rather than because Hazelcast
 * needs it: a key which nobody reads back for a human costs nothing as a digest, and
 * two VanillaBP keys of the same shape should not be bounded by two different rules.
 */
public final class ElectionCacheKey {

  /**
   * What separates the three parts. It cannot appear in a workflow module ID or in a
   * BPMN process ID, which is what keeps two different triples from producing one key.
   */
  static final char SEPARATOR = '|';

  /**
   * The number of characters a key may have before it is stored as a digest of itself.
   */
  static final int MAX_LENGTH = 512;

  private ElectionCacheKey() {

  }

  /**
   * Builds the key of one hint.
   *
   * @param workflowModuleId The ID of the workflow module
   * @param bpmnProcessId The BPMN process ID of the workflow
   * @param workflowAggregateId The workflow-aggregate ID in serialized form
   * @return The key, at most {@value #MAX_LENGTH} characters long
   */
  public static String of(
      final String workflowModuleId,
      final String bpmnProcessId,
      final String workflowAggregateId) {

    final var key = new StringBuilder()
        .append(workflowModuleId)
        .append(SEPARATOR)
        .append(bpmnProcessId)
        .append(SEPARATOR)
        .append(workflowAggregateId)
        .toString();

    return StoredKey.of(key, MAX_LENGTH);

  }

}
