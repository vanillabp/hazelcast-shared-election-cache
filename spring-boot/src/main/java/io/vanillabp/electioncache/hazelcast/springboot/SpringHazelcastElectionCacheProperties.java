package io.vanillabp.electioncache.hazelcast.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.vanillabp.electioncache.hazelcast.HazelcastElectionCacheProperties;

/**
 * Binds the section of this cache in Spring Boot to the model which does not depend on a platform.
 * The class only carries the annotation, and it lets the configuration processor write IDE
 * metadata for the inherited properties. Every default, every check and every guiding sentence is
 * in {@link HazelcastElectionCacheProperties}.
 * <p>
 * The section sits INSIDE the one the platform already owns
 * (<code>vanillabp.workflow-adapter-cache</code>). A team which raises the lifetime of a hint and a
 * team which points the members at a Kubernetes service configure the same cache. Two binding
 * classes with the same prefix can live side by side, and this one ignores the keys of the
 * platform.
 */
@ConfigurationProperties(HazelcastElectionCacheProperties.SECTION)
public class SpringHazelcastElectionCacheProperties extends HazelcastElectionCacheProperties {

  /**
   * Spring Boot creates the class and passes what it found to the inherited setters. Nothing is
   * added here, on purpose. So a default is changed in one place only.
   */
  public SpringHazelcastElectionCacheProperties() {

  }

}
