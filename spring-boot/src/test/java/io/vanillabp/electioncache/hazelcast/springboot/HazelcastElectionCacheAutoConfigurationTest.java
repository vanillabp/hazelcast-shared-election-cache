package io.vanillabp.electioncache.hazelcast.springboot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import io.vanillabp.electioncache.hazelcast.ElectionCacheMember;
import io.vanillabp.electioncache.hazelcast.HazelcastElectionCacheProperties;
import io.vanillabp.integration.adapter.migration.processservice.InMemoryWorkflowAdapterCache;
import io.vanillabp.integration.spi.WorkflowAdapterCache;
import io.vanillabp.integration.test.utils.CapturedOutput;
import io.vanillabp.integration.test.utils.FreePortUtil;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * What an application on Spring Boot has to write to share its elections: the
 * dependency, and the settings which say where the other nodes are. This test is that
 * claim - it boots the auto-configuration the way an application does and asks the
 * context for the bean VanillaBP consults.
 */
@ExtendWith(SuppressOutputExtension.class)
public class HazelcastElectionCacheAutoConfigurationTest {

  private static final String MODULE = "taxi-ride";

  private static final String PROCESS = "ride";

  /**
   * A cluster of this test's own, and a range of addresses to look in rather than one:
   * which member takes which port does not matter, a seed list is a list of addresses
   * to try.
   */
  private static final int BASE_PORT = FreePortUtil.getFreePort();

  private static final List<String> SEEDS = List
      .of("127.0.0.1:"
          + BASE_PORT,
          "127.0.0.1:"
              + (BASE_PORT + 1),
          "127.0.0.1:"
              + (BASE_PORT + 2));

  private final ApplicationContextRunner theApplication = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(HazelcastElectionCacheAutoConfiguration.class))
      .withPropertyValues(
          "spring.application.name=taxi-ride",
          "vanillabp.workflow-adapter-cache.hazelcast.cluster-name=spring-auto-configuration-test",
          "vanillabp.workflow-adapter-cache.hazelcast.discovery=members",
          "vanillabp.workflow-adapter-cache.hazelcast.members="
              + String.join(",", SEEDS),
          "vanillabp.workflow-adapter-cache.hazelcast.port="
              + BASE_PORT,
          "vanillabp.workflow-adapter-cache.hazelcast.alone-reminder-interval=PT1H");

  @Test
  @DisplayName("The dependency alone contributes the cache, and its lifetime is the context's")
  public void theDependencyContributesTheCache() {

    final var instanceOfTheApplication = new java.util.concurrent.atomic.AtomicReference<HazelcastInstance>();

    theApplication
        .run(context -> {
          assertThat(context).hasSingleBean(WorkflowAdapterCache.class);
          assertThat(context.getBean(WorkflowAdapterCache.class))
              .isInstanceOf(ManagedElectionCache.class);

          final var cache = context.getBean(ManagedElectionCache.class);
          assertThat(cache.clusterName()).isEqualTo("spring-auto-configuration-test");
          assertThat(cache.instance().getLifecycleService().isRunning()).isTrue();
          instanceOfTheApplication.set(cache.instance());
        });

    // the context which built the member took it down again
    assertThat(instanceOfTheApplication.get().getLifecycleService().isRunning()).isFalse();

  }

  @Test
  @DisplayName("The cache of this application really is the cache of the other nodes")
  public void theCacheIsSharedWithAnotherNode() {

    final var anotherNode = ElectionCacheMember
        .start(
            propertiesOfAnotherNode("spring-auto-configuration-test"),
            "taxi-ride",
            Duration.ofHours(1),
            Duration.ofMinutes(5),
            null);

    try {
      theApplication
          .run(context -> {
            final var cache = context.getBean(WorkflowAdapterCache.class);

            cache.put(MODULE, PROCESS, "1", "camunda8");

            // read through the OTHER member, which is what a second node of the
            // application is
            assertThat(anotherNode.cache().get(MODULE, PROCESS, "1")).contains("camunda8");

            anotherNode.cache().put(MODULE, PROCESS, "2", "camunda7");
            assertThat(cache.get(MODULE, PROCESS, "2")).contains("camunda7");
          });
    } finally {
      anotherNode.close();
    }

  }

  @Test
  @DisplayName("The two lifetimes are the platform's properties, not ours")
  public void theLifetimesComeFromThePlatformsSection() {

    theApplication
        .withPropertyValues(
            "vanillabp.workflow-adapter-cache.time-to-live=PT1H",
            "vanillabp.workflow-adapter-cache.ended-time-to-live=PT1S")
        .run(context -> {
          final var cache = context.getBean(WorkflowAdapterCache.class);

          cache.put(MODULE, PROCESS, "living", "camunda8");
          cache.putEnded(MODULE, PROCESS, "ended", "camunda8");

          await(
              () -> cache.get(MODULE, PROCESS, "ended").isEmpty(),
              "the hint of the ended workflow to expire after a second");
          assertThat(cache.get(MODULE, PROCESS, "living")).contains("camunda8");
        });

  }

  @Test
  @DisplayName("An application which brings its own cache keeps it")
  public void anApplicationsOwnCacheWins() {

    theApplication
        .withUserConfiguration(AnApplicationWithItsOwnCache.class)
        .run(context -> {
          assertThat(context).hasSingleBean(WorkflowAdapterCache.class);
          assertThat(context.getBean(WorkflowAdapterCache.class))
              .isInstanceOf(InMemoryWorkflowAdapterCache.class);
          assertThat(context).doesNotHaveBean(ManagedElectionCache.class);
        });

  }

  @Test
  @DisplayName("An environment which should not form a cluster switches the cache off")
  public void theCacheCanBeSwitchedOff() {

    final var membersRunningBefore = runningMembers();

    theApplication
        .withPropertyValues("vanillabp.workflow-adapter-cache.hazelcast.enabled=false")
        .run(context -> {
          assertThat(context).doesNotHaveBean(WorkflowAdapterCache.class);
          assertThat(context).doesNotHaveBean(ManagedElectionCache.class);
          // and no member was started: what would have started one is the bean above
          assertThat(runningMembers()).isEqualTo(membersRunningBefore);
        });

  }

  @Test
  @DisplayName("A configuration which cannot work ends the boot naming the property")
  public void aBrokenConfigurationEndsTheBoot() {

    theApplication
        .withPropertyValues("vanillabp.workflow-adapter-cache.hazelcast.members=")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasMessageContaining(HazelcastElectionCacheProperties.MEMBERS_PROPERTY);
        });

  }

  @Test
  @DisplayName("A node which comes up alone says so, naming its cluster")
  public void aNodeWhichIsAloneSaysSo(
      final CapturedOutput output) {

    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(HazelcastElectionCacheAutoConfiguration.class))
        .withPropertyValues(
            "spring.application.name=taxi-ride",
            "vanillabp.workflow-adapter-cache.hazelcast.cluster-name=spring-alone-test",
            "vanillabp.workflow-adapter-cache.hazelcast.discovery=members",
            "vanillabp.workflow-adapter-cache.hazelcast.members=127.0.0.1:"
                + (BASE_PORT + 10),
            "vanillabp.workflow-adapter-cache.hazelcast.port="
                + (BASE_PORT + 10))
        .run(context -> {
          assertThat(context).hasNotFailed();

          final var logged = output.getAll();
          assertThat(logged).contains("spring-alone-test");
          assertThat(logged).contains("1 member(s)");
          assertThat(logged).contains("consists of this node alone");
        });

  }

  @Test
  @DisplayName("A Hazelcast the application provides is used instead of a second member")
  public void theApplicationsInstanceIsUsed() {

    theApplication
        .withUserConfiguration(AnApplicationWithItsOwnHazelcast.class)
        .run(context -> {
          final var cache = context.getBean(ManagedElectionCache.class);
          assertThat(cache.clusterName()).isEqualTo("the-applications-own-cluster");
          // the application's instance itself, not a second member beside it
          assertThat(cache.instance()).isSameAs(context.getBean(HazelcastInstance.class));

          context.getBean(WorkflowAdapterCache.class).put(MODULE, PROCESS, "1", "camunda8");
          assertThat(
              context
                  .getBean(HazelcastInstance.class)
                  .getMap("vanillabp-election-cache")
                  .get("taxi-ride|ride|1"))
              .isEqualTo("camunda8");
        });

  }

  /**
   * The configuration of a member which is not this application: same cluster, same
   * seeds, a port of its own.
   */
  private static HazelcastElectionCacheProperties propertiesOfAnotherNode(
      final String clusterName) {

    final var properties = new HazelcastElectionCacheProperties();
    properties.setClusterName(clusterName);
    properties.setDiscovery(HazelcastElectionCacheProperties.Discovery.MEMBERS);
    properties.setMembers(SEEDS);
    properties.setPort(BASE_PORT);
    properties.setPortAutoIncrement(true);
    properties.setAloneReminderInterval(Duration.ofHours(1));
    return properties;

  }

  /**
   * How many Hazelcast members are running in this JVM. Only ever compared against a
   * reading of its own: Spring keeps every test context until the JVM exits, so the
   * members of whatever ran before are still in this list, and an absolute number would
   * depend on the order the test classes happen to run in.
   */
  private static int runningMembers() {

    return Hazelcast.getAllHazelcastInstances().size();

  }

  private static void await(
      final java.util.function.BooleanSupplier condition,
      final String whatWasExpected) throws InterruptedException {

    final var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("Waited 30 seconds in vain: "
        + whatWasExpected);

  }

  /**
   * An application which already runs Hazelcast. A second member in the same JVM would
   * be waste and a second cluster to reason about, so the cache moves into this one.
   */
  @Configuration(proxyBeanMethods = false)
  static class AnApplicationWithItsOwnHazelcast {

    @Bean(destroyMethod = "close")
    ElectionCacheMember theApplicationsOwnHazelcast() {

      final var properties = propertiesOfAnotherNode("the-applications-own-cluster");
      properties.setMembers(List.of("127.0.0.1:"
          + (BASE_PORT + 20)));
      properties.setPort(BASE_PORT + 20);

      return ElectionCacheMember
          .start(properties, "taxi-ride", Duration.ofHours(1), Duration.ofMinutes(5), null);

    }

    @Bean
    HazelcastInstance theApplicationsOwnInstance(
        final ElectionCacheMember member) {

      return member.instance();

    }

  }

  /**
   * An application whose infrastructure is neither Hazelcast nor anything this
   * repository supports writes its own cache, and that stays a first-class case.
   */
  @Configuration(proxyBeanMethods = false)
  static class AnApplicationWithItsOwnCache {

    @Bean
    WorkflowAdapterCache theApplicationsOwnCache() {

      return new InMemoryWorkflowAdapterCache();

    }

  }

}
