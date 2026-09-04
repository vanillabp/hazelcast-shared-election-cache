package io.vanillabp.electioncache.hazelcast.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.vanillabp.electioncache.hazelcast.HazelcastElectionCacheProperties;

/**
 * Spring Boot's binding of this cache's own section onto the platform-neutral model.
 * The class exists to carry the annotation and to let the configuration processor write
 * IDE metadata for the inherited properties; every default, every check and every
 * guiding sentence lives in {@link HazelcastElectionCacheProperties}.
 * <p>
 * The section sits INSIDE the one the platform already owns
 * (<code>vanillabp.workflow-adapter-cache</code>), because a team which raises the
 * lifetime of a hint and a team which points the members at a Kubernetes service are
 * configuring the same cache. Same-prefix binding classes coexist, and the keys of the
 * platform's own view are ignored by this one.
 */
@ConfigurationProperties(HazelcastElectionCacheProperties.SECTION)
public class SpringHazelcastElectionCacheProperties extends HazelcastElectionCacheProperties {

}
