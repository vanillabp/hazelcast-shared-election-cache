package io.vanillabp.electioncache.hazelcast.springboot;

import java.util.Optional;

import org.springframework.beans.factory.DisposableBean;

import com.hazelcast.core.HazelcastInstance;

import io.vanillabp.electioncache.hazelcast.ElectionCacheMember;
import io.vanillabp.integration.spi.WorkflowAdapterCache;

/**
 * The cache as a Spring bean whose destruction takes the Hazelcast member with it. The
 * bean VanillaBP asks for is a {@link WorkflowAdapterCache}, and Spring destroys what
 * it holds by that type, so the member's lifetime has to hang off the cache rather than
 * off a bean of its own: a second bean would have to be created for its side effect and
 * would be a candidate for removal by whoever reads the configuration next.
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
   * The cluster the hints are shared in - read by the tests and by whoever injects this
   * bean to see where it landed.
   *
   * @return The cluster name
   */
  String clusterName() {

    return member.clusterName();

  }

  /**
   * The Hazelcast the hints live in: the member this bean started, or the instance the
   * application provided. Read by the tests, which judge this instance rather than the
   * JVM-wide list of instances - Spring keeps every test context until the JVM exits, so
   * that list carries the members of whatever ran before.
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
