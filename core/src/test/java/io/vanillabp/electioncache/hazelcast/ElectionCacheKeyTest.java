package io.vanillabp.electioncache.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vanillabp.integration.spi.StoredKey;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * What one hint is filed under. The key is the only thing of this cache which two
 * nodes have to agree on without talking about it, so its shape is pinned here: a
 * changed separator or a changed boundary would make the hints of a node running the
 * new version invisible to a node running the old one during a rolling restart.
 */
@ExtendWith(SuppressOutputExtension.class)
public class ElectionCacheKeyTest {

  @Test
  @DisplayName("The three parts appear in the key, aggregate ID last")
  public void theKeyIsTheThreeParts() {

    assertThat(ElectionCacheKey.of("module", "process", "42"))
        .isEqualTo("module|process|42");

  }

  @Test
  @DisplayName("Two different triples never produce one key")
  public void differentTriplesDifferentKeys() {

    assertThat(ElectionCacheKey.of("a", "b", "c"))
        .isNotEqualTo(ElectionCacheKey.of("a", "b|c", ""));

  }

  @Test
  @DisplayName("A key longer than the boundary is stored as a digest of itself")
  public void aLongKeyIsHashed() {

    final var aggregateId = "x".repeat(ElectionCacheKey.MAX_LENGTH);

    final var key = ElectionCacheKey.of("module", "process", aggregateId);

    assertThat(key).startsWith(StoredKey.HASH_PREFIX);
    assertThat(key).hasSize(StoredKey.HASH_PREFIX.length() + 64);
    // and it is the digest of the readable key, not of something assembled differently
    assertThat(key)
        .isEqualTo(StoredKey.of("module|process|"
            + aggregateId, ElectionCacheKey.MAX_LENGTH));

  }

  @Test
  @DisplayName("A key which just fits stays readable")
  public void aKeyWhichFitsStaysReadable() {

    final var aggregateId = "x".repeat(ElectionCacheKey.MAX_LENGTH - "module|process|".length());

    assertThat(ElectionCacheKey.of("module", "process", aggregateId))
        .hasSize(ElectionCacheKey.MAX_LENGTH)
        .doesNotStartWith(StoredKey.HASH_PREFIX);

  }

}
