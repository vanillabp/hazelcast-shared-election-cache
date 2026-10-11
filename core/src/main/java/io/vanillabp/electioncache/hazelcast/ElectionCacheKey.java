package io.vanillabp.electioncache.hazelcast;

import io.vanillabp.integration.spi.StoredKey;

/**
 * The key of one hint. It is built from the three strings the platform hands over: the workflow
 * module, the BPMN process and the workflow-aggregate ID, serialized the way the outbox does it.
 * <p>
 * The aggregate ID comes last, because it is the only part which may contain any character. It
 * may be a composite business key, a URN, or whatever the domain model uses as an identity. The
 * two parts before it cannot contain the separator. A BPMN process ID is an XML name, and a
 * workflow module ID is a configuration key.
 * <p>
 * The length is limited the way the platform limits the keys it stores ({@link StoredKey}).
 * Hazelcast does not need that. But nobody reads such a key back for a human, so a digest costs
 * nothing. And two VanillaBP keys of the same shape should not follow two different rules.
 */
public final class ElectionCacheKey {

  /**
   * Separates the three parts. It cannot appear in a workflow module ID or in a BPMN process ID.
   * So two different triples never make the same key.
   */
  static final char SEPARATOR = '|';

  /**
   * How many characters a key may have. A longer key is stored as a digest of itself.
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
