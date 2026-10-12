package io.vanillabp.electioncache.hazelcast.springboot.it;

import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.vanillabp.integration.spi.AggregatePersistenceAware;
import io.vanillabp.integration.spi.PhaseTwoOutbox;
import io.vanillabp.integration.spi.TransactionRunner;

/**
 * The smallest application which still starts VanillaBP: a BPMS adapter on the classpath, a
 * workflow module without BPMN files, and test doubles for the three beans the platform needs at
 * startup.
 * <p>
 * It is a real {@code @SpringBootApplication}, not a list of configuration classes. The test is
 * about what such a list would take away: an application adds the dependency and gets the shared
 * cache. That happens through auto-configuration and its order.
 * <p>
 * Nothing here ever runs a workflow. The doubles throw when they are used. That keeps the test
 * honest about what it shows.
 */
@SpringBootApplication
public class ElectionCacheTestApplication {

  /**
   * @return The owner of an aggregate which nobody else claims. This application persists nothing
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
   * @return The outbox the platform needs at startup. No workflow is ever started here
   */
  @Bean
  public PhaseTwoOutbox noOutboxAtAll() {

    return call -> {
      throw new UnsupportedOperationException("this application starts no workflow");
    };

  }

  /**
   * @return The unit of work an application brings, here one which just runs the work. Nothing
   *         is persisted, so there is nothing to commit
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
