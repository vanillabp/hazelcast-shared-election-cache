package io.vanillabp.electioncache.hazelcast.springboot.it;

import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.vanillabp.integration.spi.AggregatePersistenceAware;
import io.vanillabp.integration.spi.PhaseTwoOutbox;
import io.vanillabp.integration.spi.TransactionRunner;

/**
 * An application as small as one can be and still boot VanillaBP: a BPMS adapter on the
 * classpath, a workflow module with no BPMN files, and doubles for the three things the
 * platform wants resolvable at startup.
 * <p>
 * It is a real {@code @SpringBootApplication} rather than a list of configuration
 * classes, because the claim under test is exactly what the list would take away: an
 * application adds the dependency and gets the shared cache, which happens through
 * auto-configuration and its ordering.
 * <p>
 * Nothing here ever runs a workflow. The doubles throw when they are used, which is what
 * keeps this test honest about what it proves.
 */
@SpringBootApplication
public class ElectionCacheTestApplication {

  /**
   * @return Who owns an aggregate nobody else claims - this application persists nothing
   */
  @Bean
  public AggregatePersistenceAware<Object> noPersistenceAtAll() {

    return new AggregatePersistenceAware<>() {

      @Override
      public Class<Object> getAggregateClass() {
        return Object.class;
      }

      @Override
      public Object save(
          final Object aggregate) {
        throw new UnsupportedOperationException("this application persists nothing");
      }

      @Override
      public Object getAggregateId(
          final Object aggregate) {
        throw new UnsupportedOperationException("this application persists nothing");
      }

      @Override
      public Object loadById(
          final Object aggregateId) {
        throw new UnsupportedOperationException("this application persists nothing");
      }

      @Override
      public Class<?> getAggregateIdType() {
        return null;
      }

    };

  }

  /**
   * @return The outbox the platform wants resolvable - no workflow is ever started here
   */
  @Bean
  public PhaseTwoOutbox noOutboxAtAll() {

    return call -> {
      throw new UnsupportedOperationException("this application starts no workflow");
    };

  }

  /**
   * @return The unit of work an application brings, as a pass-through: nothing is
   *         persisted, so there is nothing to commit
   */
  @Bean
  public TransactionRunner passThroughTransactionRunner() {

    return new TransactionRunner() {

      @Override
      public <T> T requireNew(
          final Supplier<T> work) {
        return work.get();
      }

      @Override
      public <T> T inCurrent(
          final Supplier<T> work) {
        return work.get();
      }

      @Override
      public boolean isRollbackOnly() {
        return false;
      }

    };

  }

}
