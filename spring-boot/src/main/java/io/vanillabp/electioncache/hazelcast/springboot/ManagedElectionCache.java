package io.vanillabp.electioncache.hazelcast.springboot;

import java.util.Optional;

import org.springframework.beans.factory.DisposableBean;

import com.hazelcast.core.HazelcastInstance;

import io.vanillabp.electioncache.hazelcast.ElectionCacheMember;
import io.vanillabp.integration.spi.WorkflowAdapterCache;

/**
 * The cache as a Spring bean. When Spring destroys the bean, the Hazelcast member goes with it.
 * VanillaBP asks for a bean of type {@link WorkflowAdapterCache}, and Spring destroys what it
 * holds by that type. So the lifetime of the member hangs on the cache, not on a bean of its own.
 * A second bean would exist only for its side effect, and the next reader of the configuration
 * might remove it.
 */
class ManagedElectionCache implements WorkflowAdapterCache, DisposableBean {

  private final ElectionCacheMember member;

  private final WorkflowAdapterCache delegate;

  ManagedElectionCache(
      final ElectionCacheMember member) {

    this.member = member;
    this.delegate = member.cache();

  }

  /**
   * The cluster the hints are shared in. The tests read it, and so does anybody who injects this
   * bean to see where it ended up.
   *
   * @return The cluster name
   */
  String clusterName() {

    return member.clusterName();

  }

  /**
   * The Hazelcast the hints live in: the member this bean started, or the instance the application
   * provided. The tests check this instance, not the list of all instances of the JVM. Spring keeps
   * every test context until the JVM exits, so that list also holds the members of every test which
   * ran before.
   *
   * @return The instance
   */
  HazelcastInstance instance() {

    return member.instance();

  }

  @Override
  public Optional<String> get(
      final String workflowModuleId,
      final String bpmnProcessId,
      final String workflowAggregateId) {

    return delegate.get(workflowModuleId, bpmnProcessId, workflowAggregateId);

  }

  @Override
  public void put(
      final String workflowModuleId,
      final String bpmnProcessId,
      final String workflowAggregateId,
      final String adapterId) {

    delegate.put(workflowModuleId, bpmnProcessId, workflowAggregateId, adapterId);

  }

  @Override
  public void putEnded(
      final String workflowModuleId,
      final String bpmnProcessId,
      final String workflowAggregateId,
      final String adapterId) {

    delegate.putEnded(workflowModuleId, bpmnProcessId, workflowAggregateId, adapterId);

  }

  @Override
  public void invalidate(
      final String workflowModuleId,
      final String bpmnProcessId,
      final String workflowAggregateId) {

    delegate.invalidate(workflowModuleId, bpmnProcessId, workflowAggregateId);

  }

  @Override
  public void destroy() {

    member.close();

  }

}
