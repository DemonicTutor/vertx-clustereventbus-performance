/*
 * Copyright 2020 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.spi.cluster.hazelcast.impl;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.Message;
import com.hazelcast.topic.MessageListener;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.spi.cluster.NodeSelector;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.spi.cluster.RegistrationUpdateEvent;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Thomas Segismont
 */
public class SubsMapHelper implements MessageListener<SubsMapHelper.RegistrationMessage> {

  static final class RegistrationMessage implements DataSerializable {

    private enum MessageType {
      ADD_CONSUMER,
      REMOVE_CONSUMER,
      REMOVE_NODE;
    }

    private MessageType type;
    private Map.Entry<String, RegistrationInfo> infos;
    private Set<String> nodeIds;

    private RegistrationMessage() {
    }

    private RegistrationMessage(final MessageType type, Map.Entry<String, RegistrationInfo> infos) {
      this.type = type;
      this.infos = infos;
      this.nodeIds = null;
    }

    private RegistrationMessage(final MessageType type, Set<String> nodeIds) {
      this.type = type;
      this.infos = null;
      this.nodeIds = nodeIds;
    }

    MessageType getType() {
      return type;
    }

    Map.Entry<String, RegistrationInfo> getInfos() {
      return infos;
    }

    Set<String> getNodeIds() {
      return nodeIds;
    }

    @Override
    public void writeData(ObjectDataOutput dataOutput) throws IOException {
      dataOutput.writeUTF(type.name());
      if (null != infos) {
        dataOutput.writeBoolean(true);
        dataOutput.writeUTF(infos.getKey());
        dataOutput.writeUTF(infos.getValue().nodeId());
        dataOutput.writeLong(infos.getValue().seq());
        dataOutput.writeBoolean(infos.getValue().localOnly());
      } else {
        dataOutput.writeBoolean(false);
        dataOutput.writeUTF(String.join(",", nodeIds));
      }
    }

    @Override
    public void readData(ObjectDataInput dataInput) throws IOException {
      type = MessageType.valueOf(dataInput.readUTF());
      if (dataInput.readBoolean()) {
        infos = Map.entry(dataInput.readUTF(), new RegistrationInfo(dataInput.readUTF(), dataInput.readLong(), dataInput.readBoolean()));
      } else {
        nodeIds = Set.copyOf(Arrays.asList(dataInput.readUTF().split(",")));
      }
    }
  }

  private static final Logger log = LoggerFactory.getLogger(SubsMapHelper.class);

  private final VertxInternal vertx;
  private final NodeSelector nodeSelector;

  private final ConcurrentMap<String, List<RegistrationInfo>> registrations = new ConcurrentHashMap<>();
  private final String nodeUuid;
  private final ITopic<RegistrationMessage> topic;
  private final UUID listenerId;

  public SubsMapHelper(VertxInternal vertx, HazelcastInstance hazelcast, NodeSelector nodeSelector) {
    this.vertx = vertx;
    this.nodeSelector = nodeSelector;
    this.nodeUuid = hazelcast.getCluster().getLocalMember().getUuid().toString();
    this.topic = hazelcast.getTopic("__vertx.subs");
    this.listenerId = this.topic.addMessageListener(this);

  }

  public List<RegistrationInfo> get(String address) {
    return registrations.getOrDefault(address, List.of());
  }

  public void put(String address, RegistrationInfo registrationInfo) {
    localPut(address, registrationInfo);
    if (!registrationInfo.localOnly()) {
      topic.publish(new RegistrationMessage(RegistrationMessage.MessageType.ADD_CONSUMER, Map.entry(address, registrationInfo)));
    }
  }

  public void remove(String address, RegistrationInfo registrationInfo) {
    localRemove(address, registrationInfo);
    if (!registrationInfo.localOnly()) {
      topic.publish(new RegistrationMessage(RegistrationMessage.MessageType.REMOVE_CONSUMER, Map.entry(address, registrationInfo)));
    }
  }

  public void removeAllForNodes(Set<String> nodeIds) {
    localRemoveNode(nodeIds);
    topic.publish(new RegistrationMessage(RegistrationMessage.MessageType.REMOVE_NODE, nodeIds));
  }

  public void republishOwnSubs() {
    registrations.forEach((key, value) -> nodeSelector.registrationsUpdated(new RegistrationUpdateEvent(key, value)));
  }

  private void localRemoveNode(Set<String> nodeIds) {
    registrations.values().forEach(infos -> infos.removeIf(info -> nodeIds.contains(info.nodeId())));
    // ToDo: maybe better to refresh only addresses that changed
    registrations.forEach((key, value) -> nodeSelector.registrationsUpdated(new RegistrationUpdateEvent(key, value)));
  }

  private void localRemove(String address, RegistrationInfo registrationInfo) {
    registrations.getOrDefault(address, List.of()).remove(registrationInfo);
    nodeSelector.registrationsUpdated(new RegistrationUpdateEvent(address, registrations.getOrDefault(address, List.of())));
  }

  private void localPut(String address, RegistrationInfo registrationInfo) {
    registrations.compute(address, (key, existing) -> {
      final var getOrCreate = null == existing ? new CopyOnWriteArrayList<RegistrationInfo>() : existing;
      getOrCreate.add(registrationInfo);
      return getOrCreate;
    });
    nodeSelector.registrationsUpdated(new RegistrationUpdateEvent(address, registrations.get(address)));
  }

  public void close() {
    topic.removeMessageListener(listenerId);
  }

  @Override
  public void onMessage(final Message<RegistrationMessage> message) {
    final var same = nodeUuid.equals(message.getPublishingMember().getUuid().toString());
    if (same) {
      return;
    }
    final var payload = message.getMessageObject();
    vertx.<Void>executeBlocking(
      promise -> {
        switch (payload.getType()) {
          case ADD_CONSUMER -> localPut(payload.getInfos().getKey(), payload.getInfos().getValue());
          case REMOVE_CONSUMER -> localRemove(payload.getInfos().getKey(), payload.getInfos().getValue());
          case REMOVE_NODE -> localRemoveNode(payload.getNodeIds());
        }
        promise.complete();
      }
    );
  }
}
