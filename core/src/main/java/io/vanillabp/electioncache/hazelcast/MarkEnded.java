package io.vanillabp.electioncache.hazelcast;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.ExtendedMapEntry;

/**
 * Marks the hint of a workflow which ENDED, and leaves a hint naming another adapter
 * alone.
 * <p>
 * The key of a hint names the aggregate and not the workflow instance, so a second
 * workflow on the same aggregate writes the same entry. An entry naming another adapter
 * can therefore only have been written by the election of that second workflow, which
 * makes the end reported here the older knowledge of the two - and the newer one stays.
 * <p>
 * Reading the entry and writing it back would be two calls with a race between them,
 * which is why this runs where the entry lives: Hazelcast executes an entry processor
 * on the member owning the key and holds that key while it does. The class travels to
 * that member, which is a member of this application: the nodes are the cluster (see
 * decision 1 in the repository's DECISIONS.md), so the class is on its classpath.
 * <p>
 * The shorter lifetime is set on the entry rather than on the map, because the map
 * holds the hints of living workflows as well.
 */
record MarkEnded(
                 String adapterId,
                 long endedTimeToLiveMillis) implements EntryProcessor<String, String, Boolean> {

  @Override
  public Boolean process(
      final Map.Entry<String, String> entry) {

    final var current = entry.getValue();

    // Hazelcast removes an expired entry before an entry processor sees it, so a value
    // which is there is a hint which is still worth something
    if ((current != null) && !current.equals(adapterId)) {
      return Boolean.FALSE;
    }

    ((ExtendedMapEntry<String, String>) entry)
        .setValue(adapterId, endedTimeToLiveMillis, TimeUnit.MILLISECONDS);

    return Boolean.TRUE;

  }

}
