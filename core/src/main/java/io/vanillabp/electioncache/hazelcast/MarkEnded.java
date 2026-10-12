package io.vanillabp.electioncache.hazelcast;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.ExtendedMapEntry;

/**
 * Marks the hint of a workflow which ENDED. A hint which names another adapter stays as it is.
 * <p>
 * The key of a hint names the aggregate, not the workflow instance. So a second workflow on the
 * same aggregate writes the same entry. An entry which names another adapter can only come from
 * the election of that second workflow. Then the end reported here is the older news of the two,
 * and the newer one stays.
 * <p>
 * Reading the entry and writing it back would be two calls, with a race between them. So this
 * runs where the entry lives. Hazelcast runs an entry processor on the member which owns the key,
 * and it locks the key meanwhile. The class travels to that member. That member is a node of this
 * application, because the nodes are the cluster (see decision 1 in the repository's
 * DECISIONS.md). So the class is on its classpath.
 * <p>
 * The shorter lifetime is set on the entry, not on the map, because the map holds the hints of
 * running workflows as well.
 */
record MarkEnded(
                 String adapterId,
                 long endedTimeToLiveMillis) implements EntryProcessor<String, String, Boolean> {

  @Override
  public Boolean process(
      final Map.Entry<String, String> entry) {

    final var current = entry.getValue();

    // Hazelcast removes an expired entry before an entry processor sees it. So a value
    // which is there is a hint which still counts
    if ((current != null) && !current.equals(adapterId)) {
      return Boolean.FALSE;
    }

    ((ExtendedMapEntry<String, String>) entry)
        .setValue(adapterId, endedTimeToLiveMillis, TimeUnit.MILLISECONDS);

    return Boolean.TRUE;

  }

}
