package com.moulberry.flashback.playback;

import com.moulberry.flashback.ext.ServerLevelExt;

import com.moulberry.flashback.packet.PlayPacketCodec;
import com.moulberry.flashback.packet.FlashbackClearResourcePack;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.IgnoredCustomPayloads;
import com.moulberry.flashback.PacketHelper;
import com.moulberry.flashback.configuration.FlashbackConfigV1;
import com.moulberry.flashback.ext.ConnectionExt;
import com.moulberry.flashback.ext.LevelChunkExt;
import com.moulberry.flashback.TempFolderProvider;
import com.moulberry.flashback.ext.ServerTickRateManagerExt;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.handler.ReplayServerKeyframeHandler;
import com.moulberry.flashback.keyframe.impl.BlockOverrideKeyframe;
import com.moulberry.flashback.keyframe.types.BlockOverrideKeyframeType;
import com.moulberry.flashback.packet.FlashbackAccurateEntityPosition;
import com.moulberry.flashback.packet.FlashbackClearEntities;
import com.moulberry.flashback.packet.FlashbackClearParticles;
import com.moulberry.flashback.packet.FlashbackForceClientTick;
import com.moulberry.flashback.packet.FlashbackInstantlyLerp;
import com.moulberry.flashback.packet.FlashbackRawCustomPayload;
import com.moulberry.flashback.packet.FlashbackRemoteExperience;
import com.moulberry.flashback.packet.FlashbackRemoteFoodData;
import com.moulberry.flashback.packet.FlashbackRemoteSelectHotbarSlot;
import com.moulberry.flashback.packet.FlashbackRemoteSetSlot;
import com.moulberry.flashback.packet.FlashbackSetBorderLerpStartTime;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.io.ReplayReader;
import com.moulberry.flashback.packet.FinishedServerTick;
import com.moulberry.flashback.record.FlashbackChunkMeta;
import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.flashback.record.Recorder;
import com.moulberry.flashback.state.KeyframeTrack;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import com.moulberry.flashback.packet.FlashbackNetworking;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import com.moulberry.flashback.io.ReplayBuffer;
import net.minecraft.network.chat.Component;
import com.moulberry.flashback.packet.PacketCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceKey;

import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public class ReplayServer extends IntegratedServer {

    private final ReplayTickRateManager replayClock = new ReplayTickRateManager(this);
    public ReplayTickRateManager tickRateManager() { return replayClock; }

    public static int REPLAY_VIEWER_IDS_START = -981723987;
    public static String REPLAY_VIEWER_NAME = "Replay Viewer";

    public volatile int jumpToTick = -1;
    public volatile boolean replayPaused = true;
    public AtomicBoolean forceApplyKeyframes = new AtomicBoolean(false);
    public AtomicBoolean sendFinishedServerTick = new AtomicBoolean(false);
    private volatile float desiredTickRate = 20.0f;
    private volatile float desiredTickRateManual = 20.0f;
    private boolean desiredFrozen = false;
    private int desiredFrozenDelay = -1;
    private boolean isFrozen = false;
    private int frozenDelay = -1;

    public volatile boolean failedToLoadRegistryDataWarning = false;
    public volatile boolean failedToSpawnPlayerWarning = false;

    private volatile long lastTimeRtc = 0L;
    private volatile long timeRtc = 0L;
    private long pendingTimeRtc = 0L;

    private boolean hasNonSpectatorReplayViewer = false;

    private int currentTick = 0;
    private volatile int targetTick = 0;
    private final int totalTicks;
    public ResourceKey<Level> spawnLevel = null;
    private final ReplayGamePacketHandler gamePacketHandler;
    public final ReplayConfigurationPacketHandler configurationPacketHandler;
    private PacketCodec<ByteBuf, Packet<? super ClientGamePacketListener>> gamePacketCodec;
    private final List<ReplayPlayer> replayViewers = new ArrayList<>();
    public boolean followLocalPlayerNextTickIfWrongDimension = false;
    public boolean isProcessingSnapshot = false;
    public List<FlashbackRawCustomPayload> customPacketsInSnapshot = new ArrayList<>();
    private boolean processedSnapshot = false;
    public volatile boolean fastForwarding = false;
    public volatile boolean hasServerResourcePack = false;

    private record BlockAtPosition(long pos, BlockState blockState) {}
    private List<BlockAtPosition> pendingBlockOverrides = new ArrayList<>();

    private int printFailedDecodePacketCount = 8;

    private final UUID playbackUUID;
    private final FlashbackMeta metadata;
    private final TreeMap<Integer, PlayableChunk> playableChunksByStart = new TreeMap<>();
    private ReplayChunkCache replayChunkCache = null;
    private final IntSet loadedChunkCacheFiles = new IntOpenHashSet();
    private ReplayReader currentReplayReader = null;

    private record RemotePack(UUID id, String url, String hash){}
    private final Map<UUID, RemotePack> oldRemotePacks = new HashMap<>();
    private final Map<UUID, RemotePack> remotePacks = new HashMap<>();
    private final Map<UUID, BossEvent> bossEvents = new HashMap<>();
    private Component tabListHeader = Component.empty();
    private Component tabListFooter = Component.empty();
    private final Map<ResourceKey<Level>, IntSet> needsPositionUpdate = new HashMap<>();

    private Component shutdownReason = null;
    private FileSystem playbackFileSystem;
    private boolean initializedWithSnapshot = false;

    private final IgnoredCustomPayloads ignoredCustomPayloads = new IgnoredCustomPayloads();

    public ReplayServer(Thread thread, Minecraft minecraft, LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem worldStem,
                        Services services, ChunkProgressListenerFactory levelLoadListener, MinecraftExt.StartReplayServerInfo info) {
        super(thread, minecraft, levelStorageAccess, packRepository, worldStem, services, levelLoadListener);
        this.playbackUUID = info.playbackUUID();
        this.playbackFileSystem = info.playbackFileSystem();
        this.metadata = info.metadata();

        this.gamePacketHandler = new ReplayGamePacketHandler(this);
        this.configurationPacketHandler = new ReplayConfigurationPacketHandler(this);

        this.gamePacketCodec = PlayPacketCodec.INSTANCE;

        int ticks = 0;
        for (Map.Entry<String, FlashbackChunkMeta> entry : this.metadata.chunks.entrySet()) {
            var chunkMetaWithPath = new PlayableChunk(entry.getValue(), this.playbackFileSystem.getPath("/"+entry.getKey()));
            this.playableChunksByStart.put(ticks, chunkMetaWithPath);
            ticks += entry.getValue().duration;
        }

        this.totalTicks = ticks;

        this.replayChunkCache = new ReplayChunkCache(this.playbackFileSystem);
    }

    public FlashbackMeta getMetadata() {
        return this.metadata;
    }

    public void replaceReplayRegistries(net.minecraft.core.LayeredRegistryAccess<net.minecraft.server.RegistryLayer> registries) {
        this.registries = registries;
        if (this.getPlayerList() instanceof com.moulberry.flashback.ext.ReplayRegistryPlayerListExt playerList)
            playerList.flashback$replaceRegistries(registries);
        if (this.currentReplayReader != null) this.currentReplayReader.changeRegistryAccess(this.registryAccess());
    }

    private boolean registryResyncPending;

    public void beginRegistryResync(ClientboundLoginPacket recorded) {
        for (ReplayPlayer viewer : this.getReplayViewers()) {
            viewer.connection.send(ReplayRegistrySync.forViewer(recorded, viewer.getId(),
                viewer.gameMode.getGameModeForPlayer(), viewer.gameMode.getPreviousGameModeForPlayer(),
                this.getPlayerList().getViewDistance(), this.getPlayerList().getSimulationDistance()));
            viewer.lastFirstPersonDataUUID = null;
            viewer.forceRespectateTickCount = 5;
            this.registryResyncPending = true;
        }
    }

    private void finishRegistryResync() {
        if (!this.registryResyncPending) return;
        this.registryResyncPending = false;
        for (ReplayPlayer viewer : this.getReplayViewers()) {
            ServerLevel level = viewer.serverLevel();
            viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(this.getPlayerList().getPlayers()));
            // Fake server players do not retain the recorded tab-list state, ping or display names.
            viewer.connection.send(this.gamePacketHandler.recordedPlayerInfo());
            viewer.connection.send(new ClientboundUpdateTagsPacket(net.minecraft.tags.TagNetworkSerialization.serializeTagsToNetwork(this.registries())));
            viewer.connection.send(new ClientboundUpdateEnabledFeaturesPacket(net.minecraft.world.flag.FeatureFlags.REGISTRY.toNames(level.enabledFeatures())));
            viewer.connection.send(new ClientboundPlayerAbilitiesPacket(viewer.getAbilities()));
            this.getPlayerList().sendLevelInfo(viewer, level);
            this.getPlayerList().sendAllPlayerInfo(viewer);
            viewer.connection.teleport(viewer.getX(), viewer.getY(), viewer.getZ(), viewer.getYRot(), viewer.getXRot());
            var chunks = level.getChunkSource().chunkMap;
            for (var holder : chunks.getChunks()) {
                var chunk = holder.getTickingChunk();
                if (chunk != null && ((ServerLevelExt)level).flashback$shouldSendChunk(chunk.getPos().toLong())
                    && chunks.getPlayers(chunk.getPos(), false).contains(viewer))
                    viewer.connection.send(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null));
            }
            for (var tracked : chunks.entityMap.values()) {
                tracked.removePlayer(viewer);
                tracked.updatePlayer(viewer);
            }
            FlashbackNetworking.send(viewer, new com.moulberry.flashback.packet.FlashbackTickRate(this.tickRateManager().tickrate(), this.tickRateManager().isFrozen()));
            FlashbackNetworking.send(viewer, FlashbackInstantlyLerp.INSTANCE);
            FlashbackNetworking.send(viewer, FlashbackClearParticles.INSTANCE);
        }
        this.forceApplyKeyframes.set(true);
    }

    private final AtomicInteger newPlayerIds = new AtomicInteger(REPLAY_VIEWER_IDS_START);
    public ReplayPlayer createPlayer(ServerLevel level, GameProfile gameProfile) {
        if (spawnLevel != null) {
            ServerLevel serverLevel = ReplayServer.this.getLevel(spawnLevel);
            if (serverLevel != null) {
                level = serverLevel;
            }
        }

        ReplayPlayer player = new ReplayPlayer(ReplayServer.this, level, gameProfile);
        player.setId(newPlayerIds.getAndDecrement());
        player.followLocalPlayerNextTick = true;
        return player;
    }

    @Override
    public boolean initServer() {
        this.setPlayerList(new PlayerList(this, this.registries(), this.playerDataStorage, 1) {
            @Override
            public net.minecraft.nbt.CompoundTag load(ServerPlayer player) {
                // Vanilla otherwise defaults every new player to the overworld, including replay actors.
                var data = new net.minecraft.nbt.CompoundTag();
                data.putString("Dimension", player.serverLevel().dimension().location().toString());
                return data;
            }

            @Override
            public void placeNewPlayer(Connection connection, ServerPlayer serverPlayer) {
                if (Flashback.getConfig().internal.filterUnnecessaryPackets) {
                    ((ConnectionExt)connection).flashback$setFilterUnnecessaryPackets();
                }
                super.placeNewPlayer(connection, serverPlayer);
                if (serverPlayer instanceof ReplayPlayer)
                    FlashbackNetworking.send(serverPlayer, new com.moulberry.flashback.packet.FlashbackTickRate(tickRateManager().tickrate(), tickRateManager().isFrozen()));
            }

            @Override
            public boolean canBypassPlayerLimit(GameProfile gameProfile) {
                return true;
            }

            @Override
            public void broadcastSystemMessage(Component component, Function<ServerPlayer, Component> function, boolean bl) {
            }

            @Override
            public void sendPlayerPermissionLevel(ServerPlayer serverPlayer) {
                if (serverPlayer instanceof FlashbackFakePlayer) {
                    return;
                }
                super.sendPlayerPermissionLevel(serverPlayer);
            }

            @Override
            public void sendAllPlayerInfo(ServerPlayer serverPlayer) {
                if (serverPlayer instanceof FlashbackFakePlayer) {
                    return;
                }
                super.sendAllPlayerInfo(serverPlayer);
            }

            @Override
            public void broadcastAll(Packet<?> packet) {
                if (packet instanceof ClientboundSetBorderLerpSizePacket) {
                    long time = ReplayServer.this.currentTick * 50L;
                    for (ReplayPlayer replayViewer : replayViewers) {
                        FlashbackNetworking.send(replayViewer, new FlashbackSetBorderLerpStartTime(time));
                    }
                }

                super.broadcastAll(packet);
            }

            @Override
            public void broadcast(@Nullable Player player, double x, double y, double z, double distance, ResourceKey<Level> resourceKey, Packet<?> packet) {
                UUID audioSourceEntity = null;

                EditorState editorState = ReplayServer.this.getEditorState();
                if (editorState != null) {
                    audioSourceEntity = editorState.audioSourceEntity;
                }

                for (ReplayPlayer replayViewer : ReplayServer.this.replayViewers) {
                    if (replayViewer != player && replayViewer.level().dimension() == resourceKey) {
                        Vec3 source = replayViewer.position();

                        if (audioSourceEntity != null) {
                            Entity entity = replayViewer.serverLevel().getEntity(audioSourceEntity);
                            if (entity != null) {
                                source = entity.position();
                            }
                        }

                        double dx = x - source.x;
                        double dy = y - source.y;
                        double dz = z - source.z;
                        if (dx*dx + dy*dy + dz*dz < distance*distance) {
                            replayViewer.connection.send(packet);
                        }
                    }
                }
            }

            @Override
            public void sendLevelInfo(ServerPlayer serverPlayer, ServerLevel serverLevel) {
                if (serverPlayer instanceof FlashbackFakePlayer) {
                    return;
                }

                super.sendLevelInfo(serverPlayer, serverLevel);

                // Send all resource packs
                FlashbackNetworking.send(serverPlayer, FlashbackClearResourcePack.INSTANCE);

                EditorState editorState = ReplayServer.this.getEditorState();
                if (!editorState.replayVisuals.disableServerResourcePack) {
                    for (RemotePack remotePack : remotePacks.values()) {
                        serverPlayer.connection.send(new ClientboundResourcePackPacket(remotePack.url, remotePack.hash, true, null));
                    }
                }

                // Tick resource packs to prevent race condition with pack being loaded in between now and tick
                ReplayServer.this.tickResourcePacks(editorState);

                // Send tab list customization
                serverPlayer.connection.send(new ClientboundTabListPacket(tabListHeader, tabListFooter));

                // Send world border
                serverPlayer.connection.send(new ClientboundInitializeBorderPacket(serverLevel.getWorldBorder()));

                // Send custom payloads found during snapshot
                ReplayServer.this.customPacketsInSnapshot.removeIf(packet -> {
                    try {
                        FlashbackNetworking.send(serverPlayer, packet);
                        return false;
                    } catch (Exception e) {
                        return true;
                    }
                });
            }

            @Override
            protected void save(ServerPlayer serverPlayer) {
            }

            @Override
            public void saveAll() {
            }

            @Override
            public void removeAll() {
                if (shutdownReason == null) {
                    super.removeAll();
                } else {
                    List<ServerPlayer> players = this.getPlayers();
                    for (ServerPlayer player : players) {
                        player.connection.disconnect(shutdownReason);
                    }
                }
            }

            @Override
            public ServerStatsCounter getPlayerStats(Player player) {
                File statsDir = this.getServer().getWorldPath(LevelResource.PLAYER_STATS_DIR).toFile();
                File statsFile = new File(statsDir, player.getUUID() + ".json");
                return new ServerStatsCounter(this.getServer(), statsFile);
            }
        });

        super.initServer();

        return true;
    }

    public void pushRemotePack(UUID uuid, String url, String hash) {
        this.remotePacks.put(uuid, new RemotePack(uuid, url, hash));
    }

    public void popRemotePack(UUID uuid) {
        this.remotePacks.remove(uuid);
    }

    public void popAllRemotePacks() {
        this.remotePacks.clear();
    }

    public void setTabListCustomization(Component header, Component footer) {
        this.tabListHeader = header;
        this.tabListFooter = footer;
        this.getPlayerList().broadcastAll(new ClientboundTabListPacket(header, footer));
    }

    public void goToReplayTick(int tick) {
        this.jumpToTick = tick;
    }

    public float getDesiredTickRate(boolean manual) {
        if (manual) {
            return this.desiredTickRateManual;
        } else {
            return this.desiredTickRate;
        }
    }

    public void setDesiredTickRate(float tickrate, boolean manual) {
        if (manual) {
            this.desiredTickRateManual = tickrate;
        } else {
            this.desiredTickRate = tickrate;
        }
    }

    public void setFrozen(boolean frozen, int delay) {
        this.desiredFrozen = frozen;
        this.desiredFrozenDelay = delay;
    }

    public int getReplayTick() {
        return this.targetTick;
    }

    private int lastReplayTick;
    private long lastTickTimeNanos;

    public double getPartialReplayTick() {
        if (this.replayPaused || Minecraft.getInstance().isPaused()) {
            return this.targetTick;
        } else {
            long currentNanos = Util.getNanos();
            long nanosPerTick = this.tickRateManager().nanosecondsPerTick();

            double partial = (currentNanos - this.lastTickTimeNanos) / (double) nanosPerTick;
            partial = Math.max(0, Math.min(1, partial));

            return this.lastReplayTick + partial;
        }
    }

    public int getTotalReplayTicks() {
        return this.totalTicks;
    }

    public boolean doClientRendering() {
        return !this.isProcessingSnapshot && !this.processedSnapshot && !this.fastForwarding;
    }

    public void updateBossBar(ClientboundBossEventPacket packet) {
        packet.dispatch(new ClientboundBossEventPacket.Handler() {
            @Override
            public void add(UUID uuid, Component component, float progress, BossEvent.BossBarColor bossBarColor, BossEvent.BossBarOverlay bossBarOverlay, boolean darkenScreen, boolean playBossMusic, boolean createWorldFog) {
                BossEvent old = bossEvents.remove(uuid);
                BossEvent newEvent = new BossEvent(uuid, component, bossBarColor, bossBarOverlay) {};
                newEvent.setProgress(progress);
                newEvent.setDarkenScreen(darkenScreen);
                newEvent.setPlayBossMusic(playBossMusic);
                newEvent.setCreateWorldFog(createWorldFog);
                if (old != null) {
                    if (old.getProgress() != progress) {
                        getPlayerList().broadcastAll(ClientboundBossEventPacket.createUpdateProgressPacket(newEvent));
                    }
                    if (!old.getName().equals(component)) {
                        getPlayerList().broadcastAll(ClientboundBossEventPacket.createUpdateNamePacket(newEvent));
                    }
                    if (!old.getColor().equals(bossBarColor) || !old.getOverlay().equals(bossBarOverlay)) {
                        getPlayerList().broadcastAll(ClientboundBossEventPacket.createUpdateStylePacket(newEvent));
                    }
                    if (old.shouldDarkenScreen() != darkenScreen || old.shouldPlayBossMusic() != playBossMusic || old.shouldCreateWorldFog() != createWorldFog) {
                        getPlayerList().broadcastAll(ClientboundBossEventPacket.createUpdatePropertiesPacket(newEvent));
                    }
                } else {
                    getPlayerList().broadcastAll(ClientboundBossEventPacket.createAddPacket(newEvent));
                }
                bossEvents.put(uuid, newEvent);
            }

            @Override
            public void remove(UUID uuid) {
                if (bossEvents.remove(uuid) != null) {
                    getPlayerList().broadcastAll(ClientboundBossEventPacket.createRemovePacket(uuid));
                }
            }

            @Override
            public void updateProgress(UUID uuid, float progress) {
                BossEvent current = bossEvents.get(uuid);
                if (current != null) {
                    current.setProgress(progress);
                    getPlayerList().broadcastAll(ClientboundBossEventPacket.createUpdateProgressPacket(current));
                }
            }

            @Override
            public void updateName(UUID uuid, Component component) {
                BossEvent current = bossEvents.get(uuid);
                if (current != null) {
                    current.setName(component);
                    getPlayerList().broadcastAll(ClientboundBossEventPacket.createUpdateNamePacket(current));
                }
            }

            @Override
            public void updateStyle(UUID uuid, BossEvent.BossBarColor bossBarColor, BossEvent.BossBarOverlay bossBarOverlay) {
                BossEvent current = bossEvents.get(uuid);
                if (current != null) {
                    current.setColor(bossBarColor);
                    current.setOverlay(bossBarOverlay);
                    getPlayerList().broadcastAll(ClientboundBossEventPacket.createUpdateStylePacket(current));
                }
            }

            @Override
            public void updateProperties(UUID uuid, boolean darkenScreen, boolean playBossMusic, boolean createWorldFog) {
                BossEvent current = bossEvents.get(uuid);
                if (current != null) {
                    current.setDarkenScreen(darkenScreen);
                    current.setPlayBossMusic(playBossMusic);
                    current.setCreateWorldFog(createWorldFog);
                    getPlayerList().broadcastAll(ClientboundBossEventPacket.createUpdatePropertiesPacket(current));
                }
            }
        });
    }

    public Collection<ReplayPlayer> getReplayViewers() {
        return this.replayViewers;
    }

    public int getLocalPlayerId() {
        return this.gamePacketHandler.localPlayerId;
    }

    public void setTimeRtc(long rtc) {
        this.pendingTimeRtc = rtc;
    }

    public void updateTimeRtc(byte delta) {
        if (this.pendingTimeRtc == 0) {
            this.pendingTimeRtc = this.timeRtc;
        }
        this.pendingTimeRtc += 50L + delta;
    }

    public boolean hasRtcData() {
        return this.lastTimeRtc != 0 || this.timeRtc != 0 || this.pendingTimeRtc != 0;
    }

    public long getInterpolatedRtc() {
        if (this.replayPaused || Minecraft.getInstance().isPaused() || this.timeRtc < this.lastTimeRtc) {
            return this.timeRtc;
        } else {
            long currentNanos = Util.getNanos();
            long nanosPerTick = this.tickRateManager().nanosecondsPerTick();

            double partial = (currentNanos - this.lastTickTimeNanos) / (double) nanosPerTick;
            partial = Math.max(0, Math.min(1, partial));

            return this.lastTimeRtc + (long)((this.timeRtc - this.lastTimeRtc) * partial);
        }
    }

    public void handleNextTick() {
        if (this.isProcessingSnapshot) {
            throw new IllegalStateException("Can't go to next tick while processing snapshot");
        }

        this.gamePacketHandler.flushPendingEntities();
        this.currentTick += 1;
        this.updateRtc();
    }

    private void updateRtc() {
        if (!this.hasRtcData()) {
            return;
        }

        if (this.pendingTimeRtc == 0 && this.timeRtc != 0) {
            this.pendingTimeRtc = this.timeRtc + 50L;
        }
        if (this.lastTimeRtc == 0 || this.lastTimeRtc > this.pendingTimeRtc) {
            this.lastTimeRtc = this.pendingTimeRtc;
        } else {
            this.lastTimeRtc = this.timeRtc;
        }
        this.timeRtc = this.pendingTimeRtc;
        this.pendingTimeRtc = 0L;
    }

    public void handleConfigurationPacket(ReplayBuffer friendlyByteBuf) {
        // Kept as a file-format action: Forge 1.20.1 serializes these packets with the PLAY codec.
        handleGamePacket(friendlyByteBuf);
    }

    public void handleGamePacket(ReplayBuffer friendlyByteBuf) {

        int start = friendlyByteBuf.readerIndex();
        // Forward the recorded bytes before decoding: Forge custom-payload decoding allocates
        // an owned buffer which only its normal packet handler would release.
        int packetId = friendlyByteBuf.readVarInt();
        if (packetId == com.moulberry.flashback.packet.PlayPacketCodec.customPayloadPacketId()) {
            this.gamePacketHandler.flushPendingEntities();
            int payloadStart = friendlyByteBuf.readerIndex();
            var id = friendlyByteBuf.readResourceLocation();
            this.ignoredCustomPayloads.setFromConfigString(Flashback.getConfig().advanced.ignoredCustomPayloads);
            if (!this.ignoredCustomPayloads.isIgnored(id)) {
                friendlyByteBuf.readerIndex(payloadStart);
                byte[] rawBytes = new byte[friendlyByteBuf.readableBytes()];
                friendlyByteBuf.readBytes(rawBytes);
                var payload = new FlashbackRawCustomPayload(rawBytes, false);
                if (this.isProcessingSnapshot) this.customPacketsInSnapshot.add(payload);
                for (ReplayPlayer viewer : this.getReplayViewers()) FlashbackNetworking.send(viewer, payload);
            }
            friendlyByteBuf.readerIndex(friendlyByteBuf.writerIndex());
            return;
        }
        friendlyByteBuf.readerIndex(start);
        Packet<? super ClientGamePacketListener> packet;
        try {
            packet = this.gamePacketCodec.decode(friendlyByteBuf);
        } catch (DecoderException decoderException) {
            // Failed to decode packet, lets try ignoring it
            if (printFailedDecodePacketCount > 0) {
                Flashback.LOGGER.error("Failed to decode packet from replay stream", decoderException);
                printFailedDecodePacketCount -= 1;
            }

            this.gamePacketHandler.flushPendingEntities();
            friendlyByteBuf.readerIndex(friendlyByteBuf.writerIndex());
            return;
        }
        if (!AllowPendingEntityPacketSet.allowPendingEntity(packet)) {
            this.gamePacketHandler.flushPendingEntities();
        }

        packet.handle(this.gamePacketHandler);
    }

    public void handleCreateLocalPlayer(ReplayBuffer friendlyByteBuf) {
        this.gamePacketHandler.flushPendingEntities();
        this.gamePacketHandler.handleCreateLocalPlayer(friendlyByteBuf);
    }

    public void handleAccuratePlayerPosition(ReplayBuffer friendlyByteBuf) {
        FlashbackConfigV1 config = Flashback.getConfig();
        if (config.advanced.disableIncreasedFirstPersonUpdates) {
            friendlyByteBuf.readerIndex(friendlyByteBuf.writerIndex());
            return;
        }

        var packet = FlashbackAccurateEntityPosition.STREAM_CODEC.decode(friendlyByteBuf);

        for (ReplayPlayer replayViewer : this.replayViewers) {
            FlashbackNetworking.send(replayViewer, packet);
        }
    }

    public void handleMoveEntities(ReplayBuffer friendlyByteBuf) {
        this.gamePacketHandler.flushPendingEntities();

        int levelCount = friendlyByteBuf.readVarInt();
        for (int i = 0; i < levelCount; i++) {
            ResourceKey<Level> dimension = friendlyByteBuf.readResourceKey(Registries.DIMENSION);
            ServerLevel level = this.levels.get(dimension);

            IntSet positionUpdateSet = null;
            if (level != null) {
                positionUpdateSet = this.needsPositionUpdate.computeIfAbsent(dimension, k -> new IntOpenHashSet());
            }

            int count = friendlyByteBuf.readVarInt();
            for (int j = 0; j < count; j++) {
                int id = friendlyByteBuf.readVarInt();
                double x = friendlyByteBuf.readDouble();
                double y = friendlyByteBuf.readDouble();
                double z = friendlyByteBuf.readDouble();
                float yaw = friendlyByteBuf.readFloat();
                float pitch = friendlyByteBuf.readFloat();
                float headYaw = friendlyByteBuf.readFloat();
                boolean onGround = friendlyByteBuf.readBoolean();

                if (level != null) {
                    Entity entity = level.getEntity(id);
                    if (entity != null) {
                        if (entity.isPassenger()) {
                            entity.setYRot(yaw);
                            entity.setXRot(pitch);
                        } else {
                            entity.moveTo(x, y, z, yaw, pitch);
                            updatePositionOfPassengers(entity);
                        }

                        entity.setYHeadRot(headYaw);
                        if (entity.onGround() != onGround) {
                            entity.setOnGround(onGround);
                        }

                        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
                            continue;
                        }

                        positionUpdateSet.add(id);
                    } else if (!this.isFrozen) {
                        byte yRot = (byte) Mth.floor(yaw * 256.0F / 360.0F);
                        byte xRot = (byte) Mth.floor(pitch * 256.0F / 360.0F);
                        this.getPlayerList().broadcastAll(PacketHelper.createTeleportForUnknown(id, x, y, z, yRot, xRot, onGround));
                    }
                }
            }
        }
    }

    private void updatePositionOfPassengers(Entity vehicle) {
        for (Entity passenger : vehicle.getPassengers()) {
            vehicle.positionRider(passenger);
            updatePositionOfPassengers(passenger);
        }
    }

    public void handleLevelChunkCached(int index) {
        ClientboundLevelChunkWithLightPacket packet = this.replayChunkCache.getOrLoad(index, this.registryAccess(), this.gamePacketCodec);

        if (packet != null) {
                this.gamePacketHandler.flushPendingEntities();

            try {
                int x = packet.getX();
                int z = packet.getZ();
                LevelChunk chunk = this.gamePacketHandler.level().getChunk(x, z);

                if (Flashback.EXPORT_JOB != null || !doesCachedChunkIdMatch(chunk, index) || this.gamePacketHandler.forceSendChunksDueToMovingPistonShenanigans.contains(ChunkPos.asLong(x, z))) {
                    packet.handle(this.gamePacketHandler);

                    if (chunk instanceof LevelChunkExt ext) {
                        ext.flashback$setCachedChunkId(index);
                    }
                }
            } catch (Exception ignored) {
                // Some mods are apparently incapable of returning a chunk when requesting it which causes an error here
                // Why a mod would be designed to do that? Only god knows
            }
        } else {
            Flashback.LOGGER.error("Missing cached level chunk: {}", index);
        }
    }

    private static boolean doesCachedChunkIdMatch(LevelChunk chunk, int chunkId) {
        if (chunk instanceof LevelChunkExt ext) {
            return ext.flashback$getCachedChunkId() == chunkId;
        }
        return false;
    }

    public static final TicketType ENTITY_LOAD_TICKET = TicketType.create("flashback_entity", java.util.Comparator.comparingLong(ChunkPos::toLong), 20);

    @Override
    public void loadLevel() {
        super.loadLevel();

        for (ServerLevel level : this.levels.values()) {
            level.noSave = true;
        }
    }

    public void closeLevel(ServerLevel serverLevel) {
        if (serverLevel == null) {
            return;
        }
        this.clearLevel(serverLevel);
        MinecraftForge.EVENT_BUS.post(new LevelEvent.Unload(serverLevel));
        try {
            serverLevel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void clearLevel(ServerLevel serverLevel) {
        if (serverLevel == null) {
            return;
        }
        for (ServerPlayer player : new ArrayList<>(serverLevel.players())) {
            if (player instanceof ReplayPlayer replayPlayer) {
                replayPlayer.lastFirstPersonDataUUID = null;
                continue;
            }
            player.discard();
        }
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof ServerPlayer) {
                continue;
            }
            entities.add(entity);
        }
        for (Entity entity : entities) {
            if (entity != null) {
                entity.discard();
            }
        }

        for (ServerPlayer player : serverLevel.players()) {
            if (player instanceof ReplayPlayer replayPlayer) {
                FlashbackNetworking.send(replayPlayer, FlashbackClearEntities.INSTANCE);
            }
        }
    }

    public EditorState getEditorState() {
        if (Flashback.isExporting()) {
            return Flashback.EXPORT_JOB.getSettings().editorState();
        }
        return EditorStateManager.get(this.metadata.replayIdentifier);
    }

    @Override
    public boolean isReady() {
        return super.isReady() && this.initializedWithSnapshot;
    }

    @Override
    public void tickServer(BooleanSupplier booleanSupplier) {
        this.replayClock.tick();
        this.nextTickTime += (long)this.replayClock.millisecondsPerTick() - 50L;
        if (!this.initializedWithSnapshot) {
            this.initializedWithSnapshot = true;

            // Play initial snapshot
            ReplayReader replayReader = this.playableChunksByStart.get(0).getOrLoadReplayReader(this.registryAccess());
            replayReader.handleSnapshot(this);
            this.gamePacketHandler.flushPendingEntities();
            this.updateRtc();
        }

        EditorState editorState = this.getEditorState();

        this.lastReplayTick = this.targetTick;
        this.lastTickTimeNanos = Util.getNanos();

        // Update list of replay viewers
        this.replayViewers.clear();
        this.hasNonSpectatorReplayViewer = false;

        for (ServerPlayer player : this.getPlayerList().getPlayers()) {
            if (player instanceof ReplayPlayer replayPlayer) {
                if (replayPlayer.isShiftKeyDown()) {
                    replayPlayer.spectatingUuid = null;
                    replayPlayer.spectatingUuidTickCount = 0;
                    replayPlayer.forceRespectateTickCount = 0;
                } else {
                    Entity cameraEntity = replayPlayer.getCamera();
                    if (cameraEntity != null && cameraEntity != replayPlayer) {
                        replayPlayer.spectatingUuid = cameraEntity.getUUID();
                        replayPlayer.spectatingUuidTickCount = 20;
                    } else if (replayPlayer.spectatingUuidTickCount > 0) {
                        replayPlayer.spectatingUuidTickCount -= 1;
                    } else {
                        replayPlayer.spectatingUuid = null;
                    }
                }
                if (!replayPlayer.isSpectator()) {
                    this.hasNonSpectatorReplayViewer = true;
                }
                this.replayViewers.add(replayPlayer);
            }
        }

        // Pause replay if game is paused (by opening the ESC pause menu for example)
        if (!this.replayPaused && Minecraft.getInstance().isPaused()) {
            this.replayPaused = true;
        }

        // Update current tick
        boolean normalPlayback = false;
        if (this.jumpToTick >= 0) {
            this.targetTick = this.jumpToTick;
            this.jumpToTick = -1;
        } else if (!this.replayPaused && this.targetTick < this.totalTicks) {
            // Normal playback
            this.targetTick += 1;
            normalPlayback = true;
        } else if (this.targetTick == this.totalTicks && this.currentTick == this.totalTicks) {
            // Pause when reaching end of replay
            this.replayPaused = true;
        }

        ReplayTickRateManager tickRateManager = this.tickRateManager();
        ((ServerTickRateManagerExt)tickRateManager).flashback$setSuppressClientUpdates(true);
        if (Flashback.EXPORT_JOB != null || this.targetTick == this.currentTick || normalPlayback || this.isFrozen) {
            this.runUpdates(booleanSupplier);
        } else {
            int realTargetTick = this.targetTick;

            if (this.targetTick < this.currentTick) {
                int minTick = this.playableChunksByStart.floorKey(this.targetTick) + 1;
                this.targetTick = Math.max(minTick, realTargetTick - 20);
            } else {
                this.targetTick = Math.max(this.currentTick+1, realTargetTick - 20);
            }

            if (this.targetTick >= realTargetTick) {
                this.targetTick = realTargetTick;
                this.runUpdates(booleanSupplier);
            } else {
                while (this.targetTick <= realTargetTick) {
                    this.fastForwarding = this.targetTick < realTargetTick;

                    this.runUpdates(booleanSupplier);

                    if (this.targetTick == realTargetTick) {
                        break;
                    } else {
                        this.targetTick += 1;
                    }
                }
                this.fastForwarding = false;
            }
        }
        ((ServerTickRateManagerExt)tickRateManager).flashback$setSuppressClientUpdates(false);

        if (this.forceApplyKeyframes.compareAndSet(true, false)) {
            ((MinecraftExt)Minecraft.getInstance()).flashback$applyKeyframes();
        }

        this.tryFollowLocalPlayer();
        this.finishRegistryResync();

        // Update first person data
        for (ReplayPlayer replayViewer : this.replayViewers) {
            // Ensure replay viewers are still spectating
            if (replayViewer.spectatingUuid != null) {
                Entity camera = replayViewer.getCamera();
                if (replayViewer.forceRespectateTickCount > 0 || camera == null || camera == replayViewer || camera.isRemoved()) {
                    Entity targetEntity = replayViewer.serverLevel().getEntity(replayViewer.spectatingUuid);
                    if (targetEntity != null && !targetEntity.isRemoved()) {
                        replayViewer.setCamera(null);
                        replayViewer.setCamera(targetEntity);
                        replayViewer.spectatingUuid = targetEntity.getUUID();

                        if (replayViewer.forceRespectateTickCount == 0) {
                            replayViewer.forceRespectateTickCount = 5;
                        }
                    }
                }
            }
            if (replayViewer.forceRespectateTickCount > 0) {
                replayViewer.forceRespectateTickCount -= 1;
            }

            Entity camera = replayViewer.getCamera();
            if (camera != replayViewer && camera instanceof Player playerCamera) {
                Inventory inventory = playerCamera.getInventory();
                if (!Objects.equals(replayViewer.lastFirstPersonDataUUID, playerCamera.getUUID())) {
                    replayViewer.lastFirstPersonDataUUID = playerCamera.getUUID();

                    replayViewer.lastFirstPersonExperienceProgress = playerCamera.experienceProgress;
                    replayViewer.lastFirstPersonTotalExperience = playerCamera.totalExperience;
                    replayViewer.lastFirstPersonExperienceLevel = playerCamera.experienceLevel;
                    FlashbackNetworking.send(replayViewer, new FlashbackRemoteExperience(playerCamera.getId(), playerCamera.experienceProgress,
                        playerCamera.totalExperience, playerCamera.experienceLevel));

                    FoodData foodData = playerCamera.getFoodData();
                    replayViewer.lastFirstPersonFoodLevel = foodData.getFoodLevel();
                    replayViewer.lastFirstPersonSaturationLevel = foodData.getSaturationLevel();
                    FlashbackNetworking.send(replayViewer, new FlashbackRemoteFoodData(playerCamera.getId(), foodData.getFoodLevel(), foodData.getSaturationLevel()));

                    replayViewer.lastFirstPersonSelectedSlot = inventory.selected;
                    FlashbackNetworking.send(replayViewer, new FlashbackRemoteSelectHotbarSlot(playerCamera.getId(), inventory.selected));

                    for (int i = 0; i < 9; i++) {
                        ItemStack hotbarItem = inventory.getItem(i);
                        replayViewer.lastFirstPersonHotbarItems[i] = hotbarItem.copy();
                        FlashbackNetworking.send(replayViewer, new FlashbackRemoteSetSlot(playerCamera.getId(), i, hotbarItem.copy()));
                    }
                } else {
                    if (replayViewer.lastFirstPersonExperienceProgress != playerCamera.experienceProgress ||
                            replayViewer.lastFirstPersonTotalExperience != playerCamera.totalExperience ||
                            replayViewer.lastFirstPersonExperienceLevel != playerCamera.experienceLevel) {
                        replayViewer.lastFirstPersonExperienceProgress = playerCamera.experienceProgress;
                        replayViewer.lastFirstPersonTotalExperience = playerCamera.totalExperience;
                        replayViewer.lastFirstPersonExperienceLevel = playerCamera.experienceLevel;
                        FlashbackNetworking.send(replayViewer, new FlashbackRemoteExperience(playerCamera.getId(), playerCamera.experienceProgress,
                            playerCamera.totalExperience, playerCamera.experienceLevel));
                    }

                    FoodData foodData = playerCamera.getFoodData();
                    if (replayViewer.lastFirstPersonFoodLevel != foodData.getFoodLevel() || replayViewer.lastFirstPersonSaturationLevel != foodData.getSaturationLevel()) {
                        replayViewer.lastFirstPersonFoodLevel = foodData.getFoodLevel();
                        replayViewer.lastFirstPersonSaturationLevel = foodData.getSaturationLevel();
                        FlashbackNetworking.send(replayViewer, new FlashbackRemoteFoodData(playerCamera.getId(), foodData.getFoodLevel(), foodData.getSaturationLevel()));
                    }

                    if (replayViewer.lastFirstPersonSelectedSlot != inventory.selected) {
                        replayViewer.lastFirstPersonSelectedSlot = inventory.selected;
                        FlashbackNetworking.send(replayViewer, new FlashbackRemoteSelectHotbarSlot(playerCamera.getId(), inventory.selected));
                    }

                    for (int i = 0; i < 9; i++) {
                        ItemStack hotbarItem = inventory.getItem(i);
                        if (!ItemStack.matches(replayViewer.lastFirstPersonHotbarItems[i], hotbarItem)) {
                            replayViewer.lastFirstPersonHotbarItems[i] = hotbarItem.copy();
                            FlashbackNetworking.send(replayViewer, new FlashbackRemoteSetSlot(playerCamera.getId(), i, hotbarItem.copy()));
                        }
                    }
                }
            } else {
                replayViewer.lastFirstPersonDataUUID = null;
            }
        }

        tickResourcePacks(editorState);

        if (this.sendFinishedServerTick.compareAndExchange(true, false)) {
            for (ReplayPlayer replayViewer : this.replayViewers) {
                FlashbackNetworking.send(replayViewer, FinishedServerTick.INSTANCE);
            }
            if (this.replayViewers.isEmpty() && Flashback.EXPORT_JOB != null) {
                Flashback.EXPORT_JOB.onFinishedServerTick();
            }
        }
    }

    private void tickResourcePacks(EditorState editorState) {
        // Vanilla 1.20.1 has one selected server pack at a time.
        boolean disabled = editorState.replayVisuals.disableServerResourcePack;
        if (this.remotePacks.isEmpty() || disabled) {
            if (!this.oldRemotePacks.isEmpty()) {
                for (ReplayPlayer player : this.replayViewers) FlashbackNetworking.send(player, FlashbackClearResourcePack.INSTANCE);
                this.oldRemotePacks.clear();
            }
        } else if (!this.remotePacks.equals(this.oldRemotePacks)) {
            RemotePack selected = this.remotePacks.values().iterator().next();
            this.getPlayerList().broadcastAll(new ClientboundResourcePackPacket(selected.url, selected.hash, true, null));
            this.oldRemotePacks.clear();
            this.oldRemotePacks.putAll(this.remotePacks);
        }
        this.hasServerResourcePack = !this.remotePacks.isEmpty();
    }

    private void runUpdates(BooleanSupplier booleanSupplier) {
        this.desiredTickRate = 20.0f;
        this.desiredFrozen = false;
        this.getEditorState().applyKeyframes(new ReplayServerKeyframeHandler(this), this.targetTick);

        if (this.desiredFrozen && this.frozenDelay < 0) {
            if (this.desiredFrozenDelay <= 0) {
                this.frozenDelay = 1;
            } else if (this.desiredFrozenDelay <= 5) {
                this.frozenDelay = 2;
            } else {
                this.frozenDelay = 3;
            }
        }

        float tickRate = this.desiredTickRate;
        if (Flashback.EXPORT_JOB == null) {
            tickRate *= this.desiredTickRateManual / 20f;
        }

        if (this.desiredFrozen) {
            if (this.frozenDelay > 0) {
                this.frozenDelay -= 1;
            }
            if (this.frozenDelay == 0) {
                this.isFrozen = true;
            }
        } else {
            this.frozenDelay = -1;
            this.isFrozen = false;
        }

        // Update tick rate & frozen state
        ReplayTickRateManager tickRateManager = this.tickRateManager();
        if (tickRateManager.tickrate() != tickRate) {
            tickRateManager.setTickRate(tickRate);
        }
        if (tickRateManager.isFrozen() != isFrozen) {
            tickRateManager.setFrozen(isFrozen);
        }

        boolean tickChanged = this.targetTick != this.currentTick;

        if (this.isFrozen && this.targetTick >= this.currentTick) {
            tickChanged = false;
        } else {
            this.handleActions();
        }

        // Add tickets for keeping entities loaded
        for (ServerLevel level : this.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
                level.getChunkSource().addRegionTicket(ENTITY_LOAD_TICKET, chunkPos, 3, chunkPos);
            }
        }

        // Tick underlying server
        super.tickServer(booleanSupplier);

        // Apply block changes
        applyBlockOverridesToTimeline();

        // Teleport entities
        if (!this.isFrozen && !this.needsPositionUpdate.isEmpty()) {
            for (Map.Entry<ResourceKey<Level>, IntSet> entry : this.needsPositionUpdate.entrySet()) {
                ResourceKey<Level> dimension = entry.getKey();
                IntSet entities = entry.getValue();

                ServerLevel level = this.getLevel(dimension);
                if (level != null) {
                    Int2ObjectMap<ChunkMap.TrackedEntity> entityMap = level.getChunkSource().chunkMap.entityMap;

                    IntIterator iterator = entities.intIterator();
                    while (iterator.hasNext()) {
                        int entityId = iterator.nextInt();

                        ChunkMap.TrackedEntity trackedEntity = entityMap.get(entityId);
                        if (trackedEntity == null) {
                            continue;
                        }

                        ServerEntity serverEntity = trackedEntity.serverEntity;

                        Vec3 trackingPosition = serverEntity.entity.trackingPosition();

                        byte quantizedYRot = (byte) Mth.floor(serverEntity.entity.getYRot() * 256.0F / 360.0F);
                        byte quantizedXRot = (byte) Mth.floor(serverEntity.entity.getXRot() * 256.0F / 360.0F);

                        if (!serverEntity.entity.isPassenger()) {
                            trackedEntity.broadcast(new ClientboundTeleportEntityPacket(serverEntity.entity));
                            serverEntity.positionCodec.setBase(trackingPosition);
                        } else {
                            trackedEntity.broadcast(new ClientboundMoveEntityPacket.Rot(entityId, quantizedYRot, quantizedXRot, serverEntity.wasOnGround));
                        }
                        serverEntity.yRotp = quantizedYRot;
                        serverEntity.xRotp = quantizedXRot;
                    }
                }
            }
            this.needsPositionUpdate.clear();
        }

        if (Flashback.EXPORT_JOB == null) {
            if (this.processedSnapshot) {
                this.processedSnapshot = false;

                for (ReplayPlayer replayViewer : this.replayViewers) {
                    FlashbackNetworking.send(replayViewer, FlashbackInstantlyLerp.INSTANCE);
                    FlashbackNetworking.send(replayViewer, FlashbackClearParticles.INSTANCE);
                }
                for (ServerLevel level : this.getAllLevels()) {
                    for (ServerPlayer player : level.players()) {
                        if (player instanceof ReplayPlayer) {
                            // Called twice, first one will update the chunk tracking & second one will update the entity tracking
                            level.getChunkSource().move(player);
                            level.getChunkSource().move(player);
                        }
                    }
                }
            }

            if (this.replayPaused) {
                if (tickChanged) {
                    if (tickRateManager.isFrozen()) {
                        tickRateManager.setFrozen(false);
                    }
                    ((ServerTickRateManagerExt)tickRateManager).flashback$setSuppressClientUpdates(false);
                    for (ReplayPlayer replayViewer : this.replayViewers) {
                        FlashbackNetworking.send(replayViewer, FlashbackForceClientTick.INSTANCE);
                    }
                    tickRateManager.setFrozen(true);
                    ((ServerTickRateManagerExt)tickRateManager).flashback$setSuppressClientUpdates(true);
                } else if (!tickRateManager.isFrozen()) {
                    tickRateManager.setFrozen(true);
                }
            } else if (tickRateManager.isFrozen() != isFrozen) {
                tickRateManager.setFrozen(isFrozen);
            }
        } else if (!tickRateManager.isFrozen()) {
            tickRateManager.setFrozen(true);
        }
    }

    private void applyBlockOverridesToTimeline() {
        if (this.pendingBlockOverrides.isEmpty()) {
            return;
        }

        List<BlockAtPosition> pendingBlockOverrides = this.pendingBlockOverrides;
        this.pendingBlockOverrides = new ArrayList<>();

        EditorState editorState = getEditorState();
        long stamp = editorState.acquireWrite();
        try {
            EditorScene scene = editorState.getCurrentScene(stamp);

            int currentTick = this.currentTick;

            boolean added = false;

            for (KeyframeTrack keyframeTrack : scene.keyframeTracks) {
                if (keyframeTrack.keyframeType == BlockOverrideKeyframeType.INSTANCE) {
                    BlockOverrideKeyframe keyframe = (BlockOverrideKeyframe) keyframeTrack.keyframesByTick.get(currentTick);
                    if (keyframe == null) {
                        keyframe = new BlockOverrideKeyframe();
                        keyframeTrack.keyframesByTick.put(currentTick, keyframe);
                    }
                    for (BlockAtPosition pendingBlockOverride : pendingBlockOverrides) {
                        long pos = pendingBlockOverride.pos;
                        int x = BlockPos.getX(pos);
                        int y = BlockPos.getY(pos);
                        int z = BlockPos.getZ(pos);
                        keyframe.setBlock(x, y, z, pendingBlockOverride.blockState);
                    }
                    added = true;
                    break;
                }
            }

            if (!added) {
                KeyframeTrack keyframeTrack = new KeyframeTrack(BlockOverrideKeyframeType.INSTANCE);

                BlockOverrideKeyframe keyframe = new BlockOverrideKeyframe();
                keyframeTrack.keyframesByTick.put(currentTick, keyframe);
                for (BlockAtPosition pendingBlockOverride : pendingBlockOverrides) {
                    long pos = pendingBlockOverride.pos;
                    int x = BlockPos.getX(pos);
                    int y = BlockPos.getY(pos);
                    int z = BlockPos.getZ(pos);
                    keyframe.setBlock(x, y, z, pendingBlockOverride.blockState);
                }

                scene.keyframeTracks.add(keyframeTrack);
            }
        } finally {
            editorState.release(stamp);
        }
    }

    private void handleActions() {
        this.targetTick = Math.max(0, Math.min(this.totalTicks, this.targetTick));

        if (this.targetTick == this.currentTick) {
            return;
        }

        Map.Entry<Integer, PlayableChunk> oldEntry = this.playableChunksByStart.floorEntry(this.currentTick);

        int duration;
        if (oldEntry != null) {
            duration = oldEntry.getValue().chunkMeta.duration;
        } else {
            duration = Recorder.CHUNK_LENGTH_SECONDS * 20;
        }
        duration = Math.max(60 * 20, duration);

        boolean shouldJump = this.targetTick < this.currentTick;
        if (this.targetTick > this.currentTick + duration) {
            if (oldEntry != null) {
                Map.Entry<Integer, PlayableChunk> targetEntry = this.playableChunksByStart.floorEntry(this.targetTick);
                shouldJump |= oldEntry.getValue() != targetEntry.getValue();
            } else {
                shouldJump = true;
            }
        }

        if (shouldJump) {
            Map.Entry<Integer, PlayableChunk> entry = this.playableChunksByStart.floorEntry(this.targetTick);
            this.playSnapshot(entry.getValue().getOrLoadReplayReader(this.registryAccess()));
            this.currentTick = entry.getKey();
        }

        Map.Entry<Integer, PlayableChunk> entry = this.playableChunksByStart.floorEntry(this.currentTick);
        if (entry == null) {
            return;
        }

        this.currentReplayReader = entry.getValue().getOrLoadReplayReader(this.registryAccess());
        if (this.currentReplayReader.isAtStart() && this.currentTick != entry.getKey()) {
            String message = "Replay reader is at wrong position. Should be at start (" + entry.getKey() + ") but instead is at " + this.currentTick;
            Flashback.LOGGER.error(message);
            this.stopWithReason(Component.literal(message));
            return;
        }
        if (this.currentTick == entry.getKey()) {
            this.currentReplayReader.resetToStart();

            if (!this.processedSnapshot && entry.getValue().chunkMeta.forcePlaySnapshot) {
                this.playSnapshot(this.currentReplayReader);
            }
        }

        EditorState editorState = getEditorState();
        long stamp = editorState.acquireRead();
        try {
            EditorScene scene = editorState.getCurrentScene(stamp);
            Map<Integer, Keyframe> blockOverrideKeyframes = null;

            for (KeyframeTrack keyframeTrack : scene.keyframeTracks) {
                if (keyframeTrack.enabled && keyframeTrack.keyframeType == BlockOverrideKeyframeType.INSTANCE) {
                    blockOverrideKeyframes = keyframeTrack.keyframesByTick;
                    break;
                }
            }

            if (blockOverrideKeyframes == null) {
                editorState.release(stamp);
                stamp = 0L;
            }

            int lastActualTick = this.currentTick;
            while (this.currentTick < this.targetTick) {
                if (lastActualTick != this.currentTick) {
                    if (blockOverrideKeyframes != null) {
                        applyBlockOverrideKeyframes(blockOverrideKeyframes, lastActualTick);
                    }
                    // Advance game time on the server
                    for (ServerLevel level : this.getAllLevels()) {
                        if (level.getLevelData() instanceof ServerLevelData serverLevelData) {
                            serverLevelData.setGameTime(serverLevelData.getGameTime() + this.currentTick - lastActualTick);
                        }
                    }
                    lastActualTick = this.currentTick;
                }

                if (!this.currentReplayReader.handleNextAction(this)) {
                    Map.Entry<Integer, PlayableChunk> newEntry = this.playableChunksByStart.floorEntry(this.currentTick);
                    if (newEntry.getValue() == entry.getValue()) {
                        this.targetTick = this.currentTick;
                        this.replayPaused = true;
                        return;
                    }
                    entry = newEntry;

                    if (newEntry.getKey() != this.currentTick) {
                        Flashback.LOGGER.error("Error processing replay: ran out of entries before expected end of PlayableChunk");
                        Flashback.LOGGER.error("Current tick: {}", this.currentTick);
                        Flashback.LOGGER.error("New entry start: {}", newEntry.getKey());
                        this.stopWithReason(Component.literal("Error processing replay: ran out of entries before expected end of PlayableChunk"));
                        return;
                    }

                    this.currentReplayReader = entry.getValue().getOrLoadReplayReader(this.registryAccess());
                    this.currentReplayReader.resetToStart();

                    if (entry.getValue().chunkMeta.forcePlaySnapshot) {
                        this.playSnapshot(this.currentReplayReader);
                    }
                }

                if (entry.getKey() + entry.getValue().chunkMeta.duration < this.currentTick) {
                    Flashback.LOGGER.error("Error processing replay: actual duration of PlayableChunk inconsistent with recorded duration");
                    Flashback.LOGGER.error("Current tick: {}", this.currentTick);
                    Flashback.LOGGER.error("PlayableChunk tick base: {}", entry.getKey());
                    Flashback.LOGGER.error("PlayableChunk duration: {}", entry.getValue().chunkMeta.duration);
                    this.stopWithReason(Component.literal("Error processing replay: actual duration of PlayableChunk inconsistent with recorded duration"));
                    return;
                }
            }

            if (lastActualTick != this.currentTick) {
                if (blockOverrideKeyframes != null) {
                    applyBlockOverrideKeyframes(blockOverrideKeyframes, lastActualTick);
                }
                // Advance game time on the server
                for (ServerLevel level : this.getAllLevels()) {
                    if (level.getLevelData() instanceof ServerLevelData serverLevelData) {
                        serverLevelData.setGameTime(serverLevelData.getGameTime() + this.currentTick - lastActualTick);
                    }
                }
                lastActualTick = this.currentTick;
            }

            if (blockOverrideKeyframes != null) {
                applyBlockOverrideKeyframes(blockOverrideKeyframes, this.currentTick);
            }
        } finally {
            if (stamp != 0) {
                editorState.release(stamp);
            }
        }
    }

    private void playSnapshot(ReplayReader replayReader) {
        this.processedSnapshot = true;

        this.clearDataForPlayingSnapshot();
        replayReader.handleSnapshot(this);
        this.gamePacketHandler.flushPendingEntities();
        this.updateRtc();

        replayReader.resetToStart();
    }

    private void applyBlockOverrideKeyframes(Map<Integer, Keyframe> blockOverrideKeyframes, int tick) {
        ServerLevel level = this.gamePacketHandler.level();
        if (level != null) {
            BlockOverrideKeyframe keyframe = (BlockOverrideKeyframe) blockOverrideKeyframes.get(tick);
            if (keyframe != null) {
                BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
                BlockState emptyState = BlockOverrideKeyframe.EMPTY_STATE;
                for (Long2ObjectMap.Entry<PalettedContainer<BlockState>> chunkEntry : keyframe.blocks.long2ObjectEntrySet()) {
                    long chunkPos = chunkEntry.getLongKey();
                    int chunkX = BlockPos.getX(chunkPos);
                    int chunkY = BlockPos.getY(chunkPos);
                    int chunkZ = BlockPos.getZ(chunkPos);

                    if (chunkY < level.getMinSection() || chunkY > (level.getMaxSection() - 1)) {
                        continue;
                    }

                    PalettedContainer<BlockState> container = chunkEntry.getValue();

                    LevelChunk levelChunk = level.getChunk(chunkX, chunkZ);
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                BlockState blockState = container.get(x, y, z);
                                if (blockState == emptyState) continue;

                                mutableBlockPos.set((chunkX << 4) + x, (chunkY << 4) + y, (chunkZ << 4) + z);
                                BlockState old = ((LevelChunkExt)levelChunk).flashback$setBlockStateWithoutUpdates(mutableBlockPos, blockState);
                                if (old != null) {
                                    level.sendBlockUpdated(mutableBlockPos, old, blockState, 3);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void clearDataForPlayingSnapshot() {
        for (ReplayPlayer replayViewer : this.replayViewers) {
            for (UUID uuid : this.bossEvents.keySet()) {
                replayViewer.connection.send(ClientboundBossEventPacket.createRemovePacket(uuid));
            }
        }
        this.bossEvents.clear();
        this.gamePacketHandler.clearDataForPlayingSnapshot();
    }

    public void blockChangeOccurred(BlockPos blockPos, BlockState result) {
        if (this.replayPaused && this.hasNonSpectatorReplayViewer) {
            this.pendingBlockOverrides.add(new BlockAtPosition(blockPos.asLong(), result));
        }
    }

    private void tryFollowLocalPlayer() {
        if (this.spawnLevel == null) {
            return;
        }

        ServerLevel currentLevel = this.getLevel(this.spawnLevel);
        if (currentLevel == null) {
            return;
        }

        Entity follow = currentLevel.getEntity(this.gamePacketHandler.localPlayerId);
        if (follow == null || (follow.getX() == 0.0 && follow.getY() == 0.0 && follow.getZ() == 0.0)) {
            return;
        }

        for (ReplayPlayer replayViewer : this.getReplayViewers()) {
            boolean shouldFollow = replayViewer.followLocalPlayerNextTick;
            if (this.followLocalPlayerNextTickIfWrongDimension) {
                shouldFollow |= replayViewer.level() != currentLevel;
            }
            if (shouldFollow) {
                replayViewer.followLocalPlayerNextTick = false;
                replayViewer.teleportTo(currentLevel, follow.getX(), follow.getY(), follow.getZ(), Set.of(),
                    follow.getYRot(), follow.getXRot());
            }
        }

        this.followLocalPlayerNextTickIfWrongDimension = false;
    }

    @Override
    public boolean haveTime() {
        // When jumping to a tick, we want to tick asap
        return (this.runningTask() || Util.getMillis() < this.nextTickTime) && this.jumpToTick == -1;
    }

    @Override
    protected void waitForTasks() {
        if (this.jumpToTick != -1 || Flashback.EXPORT_JOB != null) {
            // When jumping to a tick in an export, don't wait for the full tick
            LockSupport.parkNanos("waiting for tasks", 100000L);
        } else {
            super.waitForTasks();
        }
    }

    private void stopWithReason(Component reason) {
        this.shutdownReason = reason;
        this.halt(false);
    }

    @Override
    public boolean saveEverything(boolean bl, boolean bl2, boolean bl3) {
        return false;
    }

    @Override
    public boolean saveAllChunks(boolean bl, boolean bl2, boolean bl3) {
        return false;
    }

    @Override
    public void stopServer() {
        // Remove all levels
        for (ServerLevel level : this.levels.values()) {
            if (level == null) {
                continue;
            }
            try {
                level.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        this.levels.clear();

        // Stop server
        super.stopServer();
        TempFolderProvider.deleteTemp(TempFolderProvider.TempFolderType.SERVER, this.playbackUUID);

        if (this.playbackFileSystem != null) {
            try {
                this.playbackFileSystem.close();
            } catch (IOException e) {
                Flashback.LOGGER.error("Failed to close playback zip", e);
            }
        }

        this.replayChunkCache.clear();
        this.playableChunksByStart.clear();
    }

    public void clearReplayTempFolder() {
        Path temp = TempFolderProvider.createTemp(TempFolderProvider.TempFolderType.SERVER, this.playbackUUID);

        try {
            com.moulberry.flashback.io.ReplayTempCleanup.clearSavedData(temp);
        } catch (IOException e) {
            Flashback.LOGGER.error("Unable to delete replay temp folder", e);
        }
    }

    @Override
    public boolean isSingleplayerOwner(GameProfile gameProfile) {
        return gameProfile.getName().equals(REPLAY_VIEWER_NAME);
    }
}
