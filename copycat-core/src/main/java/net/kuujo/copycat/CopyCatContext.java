/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kuujo.copycat;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import net.kuujo.copycat.cluster.ClusterConfig;
import net.kuujo.copycat.event.Event;
import net.kuujo.copycat.event.EventContext;
import net.kuujo.copycat.event.EventHandlerRegistry;
import net.kuujo.copycat.event.Events;
import net.kuujo.copycat.log.Log;
import net.kuujo.copycat.log.LogFactory;
import net.kuujo.copycat.log.impl.FileLogFactory;
import net.kuujo.copycat.protocol.CorrelationStrategy;
import net.kuujo.copycat.protocol.TimerStrategy;
import net.kuujo.copycat.registry.Registry;
import net.kuujo.copycat.registry.impl.BasicRegistry;
import net.kuujo.copycat.registry.impl.ConcurrentRegistry;
import net.kuujo.copycat.state.State;
import net.kuujo.copycat.state.impl.RaftStateContext;

/**
 * CopyCat replica context.<p>
 *
 * The <code>CopyCatContext</code> is the primary API for creating
 * and running a CopyCat replica. Given a state machine, a cluster
 * configuration, and a log, the context will communicate with other
 * nodes in the cluster, applying and replicating state machine commands.<p>
 *
 * CopyCat uses a Raft-based consensus algorithm to perform leader election
 * and state machine replication. In CopyCat, all state changes are made
 * through the cluster leader. When a cluster is started, nodes will
 * communicate with one another to elect a leader. When a command is submitted
 * to any node in the cluster, the command will be forwarded to the leader.
 * When the leader receives a command submission, it will first replicate
 * the command to its followers before applying the command to its state
 * machine and returning the result.<p>
 *
 * In order to prevent logs from growing too large, CopyCat uses snapshotting
 * to periodically compact logs. In CopyCat, snapshots are simply log
 * entries before which all previous entries are cleared. When a node first
 * becomes the cluster leader, it will first commit a snapshot of its current
 * state to its log. This snapshot can be used to get any new nodes up to date.<p>
 *
 * CopyCat supports dynamic cluster membership changes. If the {@link ClusterConfig}
 * provided to the CopyCat context is {@link java.util.Observable}, the cluster
 * leader will observe the configuration for changes. Note that cluster membership
 * changes can only occur on the leader's cluster configuration. This is because,
 * as with all state changes, cluster membership changes must go through the leader.
 * When cluster membership changes occur, the cluster leader will log and replicate
 * the configuration change just like any other state change, and it will ensure
 * that the membership change occurs in a manner that prevents a dual-majority
 * in the cluster.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class CopyCatContext {
  private final Registry registry;
  private final ClusterConfig cluster;
  private final CopyCatConfig config;
  private final RaftStateContext state;

  public CopyCatContext(StateMachine stateMachine) {
    this(stateMachine, new FileLogFactory(), new ClusterConfig(), new CopyCatConfig());
  }

  public CopyCatContext(StateMachine stateMachine, ClusterConfig cluster) {
    this(stateMachine, new FileLogFactory(), cluster, new CopyCatConfig());
  }

  public CopyCatContext(StateMachine stateMachine, ClusterConfig cluster, Registry registry) {
    this(stateMachine, new FileLogFactory(), cluster, new CopyCatConfig(), registry);
  }

  public CopyCatContext(StateMachine stateMachine, ClusterConfig cluster, CopyCatConfig config, Registry registry) {
    this(stateMachine, new FileLogFactory(), cluster, config, registry);
  }

  public CopyCatContext(StateMachine stateMachine, LogFactory logFactory) {
    this(stateMachine, logFactory, new ClusterConfig(), new CopyCatConfig());
  }

  public CopyCatContext(StateMachine stateMachine, LogFactory logFactory, ClusterConfig cluster) {
    this(stateMachine, logFactory, cluster, new CopyCatConfig());
  }

  public CopyCatContext(StateMachine stateMachine, LogFactory logFactory, ClusterConfig cluster, CopyCatConfig config) {
    this(stateMachine, logFactory, cluster, config, new ConcurrentRegistry());
  }

  public CopyCatContext(StateMachine stateMachine, LogFactory logFactory, ClusterConfig cluster, CopyCatConfig config, Registry registry) {
    this.registry = registry;
    this.cluster = cluster;
    this.config = config;
    this.state = new RaftStateContext(stateMachine, logFactory, cluster, config, registry);
  }

  /**
   * Returns the replica configuration.
   *
   * @return The replica configuration.
   */
  public CopyCatConfig config() {
    return config;
  }

  /**
   * Returns the cluster configuration.
   *
   * @return The cluster configuration.
   */
  public ClusterConfig cluster() {
    return cluster;
  }

  /**
   * Returns the context registry.<p>
   *
   * The registry can be used to register objects that can be accessed
   * by {@link net.kuujo.copycat.protocol.Protocol} and
   * {@link net.kuujo.copycat.endpoint.Endpoint} implementations.
   *
   * @return The context registry.
   */
  public Registry registry() {
    return registry;
  }

  /**
   * Returns the context events.
   *
   * @return Context events.
   */
  public Events on() {
    return state.events();
  }

  /**
   * Returns the context for a specific event.
   *
   * @param event The event for which to return the context.
   * @return The event context.
   */
  public <T extends Event> EventContext<T> on(Class<T> event) {
    return state.events().<T>event(event);
  }

  /**
   * Returns an event handler registry for a specific event.
   *
   * @param event The event for which to return the registry.
   * @return
   */
  public <T extends Event> EventHandlerRegistry<T> event(Class<T> event) {
    return state.events().event(event);
  }

  /**
   * Returns the current replica state.
   *
   * @return The current replica state.
   */
  public State.Type state() {
    return state.state();
  }

  /**
   * Returns the current leader URI.
   *
   * @return The current leader URI.
   */
  public String leader() {
    return state.leader();
  }

  /**
   * Returns a boolean indicating whether the node is the current leader.
   *
   * @return Indicates whether the node is the current leader.
   */
  public boolean isLeader() {
    return state.isLeader();
  }

  /**
   * Starts the context.
   *
   * @return A completable future to be completed once the context has started.
   */
  public CompletableFuture<Void> start() {
    return state.start();
  }

  /**
   * Stops the context.
   *
   * @return A completable future that will be completed when the context has started.
   */
  public CompletableFuture<Void> stop() {
    return state.stop();
  }

  /**
   * Submits a command to the cluster.
   *
   * @param command The name of the command to submit.
   * @param args An ordered list of command arguments.
   * @return A completable future to be completed once the result is received.
   */
  public <R> CompletableFuture<R> submitCommand(final String command, final Object... args) {
    return state.submitCommand(command, args);
  }

  /**
   * CopyCat builder.
   *
   * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
   */
  public static class Builder {
    private CopyCatConfig config = new CopyCatConfig();
    private ClusterConfig cluster = new ClusterConfig();
    private StateMachine stateMachine;
    private LogFactory logFactory = new FileLogFactory();
    private Registry registry = new BasicRegistry();

    /**
     * Sets the copycat log factory.
     *
     * @param uri The copycat log factory.
     * @return The copycat builder.
     */
    public Builder withLogFactory(LogFactory factory) {
      this.logFactory = factory;
      return this;
    }

    /**
     * Sets the copycat log.
     *
     * @param uri The copycat log.
     * @return The copycat builder.
     */
    public Builder withLog(Log log) {
      this.logFactory = new LogFactory() {
        @Override
        public Log createLog(String name) {
          return log;
        }
      };
      return this;
    }

    /**
     * Sets the copycat configuration.
     *
     * @param uri The copycat configuration.
     * @return The copycat builder.
     */
    public Builder withConfig(CopyCatConfig config) {
      this.config = config;
      return this;
    }

    /**
     * Sets the copycat election timeout.
     *
     * @param uri The copycat election timeout.
     * @return The copycat builder.
     */
    public Builder withElectionTimeout(long timeout) {
      config.setElectionTimeout(timeout);
      return this;
    }

    /**
     * Sets the copycat heartbeat interval.
     *
     * @param uri The copycat heartbeat interval.
     * @return The copycat builder.
     */
    public Builder withHeartbeatInterval(long interval) {
      config.setHeartbeatInterval(interval);
      return this;
    }

    /**
     * Sets whether to require quorums during reads.
     *
     * @param requireQuorum Whether to require quorums during reads.
     * @return The copycat builder.
     */
    public Builder withRequireReadQuorum(boolean requireQuorum) {
      config.setRequireReadQuorum(requireQuorum);
      return this;
    }

    /**
     * Sets whether to require quorums during writes.
     *
     * @param requireQuorum Whether to require quorums during writes.
     * @return The copycat builder.
     */
    public Builder withRequireWriteQuorum(boolean requireQuorum) {
      config.setRequireWriteQuorum(requireQuorum);
      return this;
    }

    /**
     * Sets the read quorum size.
     *
     * @param quorumSize The read quorum size.
     * @return The copycat builder.
     */
    public Builder withReadQuorumSize(int quorumSize) {
      config.setReadQuorumSize(quorumSize);
      return this;
    }

    /**
     * Sets the max log size.
     *
     * @param maxSize The max log size.
     * @return The copycat builder.
     */
    public Builder withMaxLogSize(int maxSize) {
      config.setMaxLogSize(maxSize);
      return this;
    }

    /**
     * Sets the correlation strategy.
     *
     * @param strategy The correlation strategy.
     * @return The copycat builder.
     */
    public Builder withCorrelationStrategy(CorrelationStrategy<?> strategy) {
      config.setCorrelationStrategy(strategy);
      return this;
    }

    /**
     * Sets the timer strategy.
     *
     * @param strategy The timer strategy.
     * @return The copycat builder.
     */
    public Builder withTimerStrategy(TimerStrategy strategy) {
      config.setTimerStrategy(strategy);
      return this;
    }

    /**
     * Sets the cluster configuration.
     *
     * @param cluster The cluster configuration.
     * @return The copycat builder.
     */
    public Builder withClusterConfig(ClusterConfig cluster) {
      this.cluster = cluster;
      return this;
    }

    /**
     * Sets the local cluster member.
     *
     * @param uri The local cluster member URI.
     * @return The copycat builder.
     */
    public Builder withLocalMember(String uri) {
      this.cluster.setLocalMember(uri);
      return this;
    }

    /**
     * Sets the remote cluster members.
     *
     * @param uris The remote cluster member URIs.
     * @return The copycat builder.
     */
    public Builder withRemoteMembers(String... uris) {
      this.cluster.setRemoteMembers(uris);
      return this;
    }

    /**
     * Sets the remote cluster members.
     *
     * @param uris The remote cluster member URIs.
     * @return The copycat builder.
     */
    public Builder withRemoteMembers(Set<String> uris) {
      this.cluster.setRemoteMembers(uris);
      return this;
    }

    /**
     * Sets the copycat state machine.
     *
     * @param stateMachine The state machine.
     * @return The copycat builder.
     */
    public Builder withStateMachine(StateMachine stateMachine) {
      this.stateMachine = stateMachine;
      return this;
    }

    /**
     * Sets the copycat registry.
     *
     * @param registry The copycat registry.
     * @return The copycat builder.
     */
    public Builder withRegistry(Registry registry) {
      this.registry = registry;
      return this;
    }

    /**
     * Builds the copycat instance.
     *
     * @return The copycat instance.
     */
    public CopyCatContext build() {
      return new CopyCatContext(stateMachine, logFactory, cluster, config, registry);
    }

  }

}
