/*
 * Copyright 2019-2021 CloudNetService team & contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.dytanic.cloudnet.driver.network;

import com.google.common.base.Preconditions;
import de.dytanic.cloudnet.common.concurrent.ITask;
import de.dytanic.cloudnet.common.document.gson.JsonDocument;
import de.dytanic.cloudnet.driver.network.protocol.IPacket;
import de.dytanic.cloudnet.driver.network.protocol.IPacketListenerRegistry;
import de.dytanic.cloudnet.driver.network.protocol.QueryPacketManager;
import de.dytanic.cloudnet.driver.network.protocol.chunk.ChunkedPacketBuilder;
import de.dytanic.cloudnet.driver.network.protocol.chunk.ChunkedQueryResponse;
import de.dytanic.cloudnet.driver.network.protocol.defaults.DefaultPacketListenerRegistry;
import de.dytanic.cloudnet.driver.network.protocol.defaults.DefaultQueryPacketManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;

public abstract class DefaultNetworkChannel implements INetworkChannel {

  private static final AtomicLong CHANNEL_ID_COUNTER = new AtomicLong();

  private final long channelId = CHANNEL_ID_COUNTER.addAndGet(1);

  private final QueryPacketManager queryPacketManager;
  private final IPacketListenerRegistry packetRegistry;

  private final HostAndPort serverAddress;
  private final HostAndPort clientAddress;

  private final boolean clientProvidedChannel;

  private INetworkChannelHandler handler;

  public DefaultNetworkChannel(
    IPacketListenerRegistry packetRegistry,
    HostAndPort serverAddress,
    HostAndPort clientAddress,
    boolean clientProvidedChannel,
    INetworkChannelHandler handler
  ) {
    this.queryPacketManager = new DefaultQueryPacketManager(this);
    this.packetRegistry = new DefaultPacketListenerRegistry(packetRegistry);
    this.serverAddress = serverAddress;
    this.clientAddress = clientAddress;
    this.clientProvidedChannel = clientProvidedChannel;
    this.handler = handler;
  }

  @Override
  public ITask<IPacket> sendQueryAsync(@NotNull IPacket packet) {
    return this.queryPacketManager.sendQueryPacket(packet);
  }

  @Override
  public IPacket sendQuery(@NotNull IPacket packet) {
    return this.sendQueryAsync(packet).get(5, TimeUnit.SECONDS, null);
  }

  @Override
  public ITask<IPacket> registerQueryResponseHandler(UUID uniqueId) {
    // todo: this is not needed at all, remove
    return null;
  }

  @Override
  public ITask<ChunkedQueryResponse> sendChunkedPacketQuery(@NotNull IPacket packet) {
    // todo: fix this abomination
    return null;
  }

  @Override
  public boolean sendChunkedPackets(@NotNull UUID uniqueId, @NotNull JsonDocument header,
    @NotNull InputStream inputStream, int channel) throws IOException {
    return ChunkedPacketBuilder.newBuilder(channel, inputStream)
      .uniqueId(uniqueId)
      .header(header)
      .target(this)
      .complete()
      .isSuccess();
  }

  @Override
  public boolean sendChunkedPacketsResponse(@NotNull UUID uniqueId, @NotNull JsonDocument header,
    @NotNull InputStream inputStream) throws IOException {
    return this.sendChunkedPackets(uniqueId, header, inputStream, -1);
  }

  @Override
  public boolean sendChunkedPackets(@NotNull JsonDocument header, @NotNull InputStream inputStream, int channel)
    throws IOException {
    return this.sendChunkedPackets(UUID.randomUUID(), header, inputStream, channel);
  }

  @Override
  public long getChannelId() {
    return this.channelId;
  }

  @Override
  public IPacketListenerRegistry getPacketRegistry() {
    return this.packetRegistry;
  }

  @Override
  public @NotNull QueryPacketManager getQueryPacketManager() {
    return this.queryPacketManager;
  }

  @Override
  public HostAndPort getServerAddress() {
    return this.serverAddress;
  }

  @Override
  public HostAndPort getClientAddress() {
    return this.clientAddress;
  }

  @Override
  public boolean isClientProvidedChannel() {
    return this.clientProvidedChannel;
  }

  @Override
  public INetworkChannelHandler getHandler() {
    return this.handler;
  }

  @Override
  public void setHandler(INetworkChannelHandler handler) {
    this.handler = handler;
  }

}
