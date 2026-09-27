package com.moulberry.flashback.record;

import com.moulberry.flashback.packet.PlayPacketCodec;
import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Pair;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ModListHelper;
import com.moulberry.flashback.PacketHelper;
import com.moulberry.flashback.RegistryMetaHelper;
import com.moulberry.flashback.action.*;
import com.moulberry.flashback.compat.BobbyUtil;
import com.moulberry.flashback.compat.DistantHorizonsSupport;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.io.ReplayWriter;
import com.moulberry.flashback.mixin.compat.bobby.FakeChunkManagerAccessor;
import com.moulberry.flashback.packet.FlashbackAccurateEntityPosition;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.*;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.ConnectionProtocol;
import com.moulberry.flashback.io.ReplayBuffer;
import net.minecraft.network.chat.Component;
import com.moulberry.flashback.packet.PacketCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.*;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;

public class Recorder {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int CHUNK_LENGTH_SECONDS = 5 * 60;

    private final AsyncReplaySaver asyncReplaySaver;
    private PacketCodec<ByteBuf, Packet<? super ClientGamePacketListener>> gamePacketCodec;

    private int writtenTicksInChunk = 0;
    private int writtenTicks = 0;
    private final FlashbackMeta metadata = new FlashbackMeta();
    private boolean hasTakenScreenshot = false;
    private NativeImage finishedScreenshot = null;

    private record PacketWithPhase(Packet<?> packet, ConnectionProtocol phase){}
    private final Queue<PacketWithPhase> pendingPackets = new ConcurrentLinkedQueue<>();

    private record Position(double x, double y, double z, float yaw, float pitch, float headYRot, boolean onGround) {
        public Position {
            yaw = Mth.wrapDegrees(yaw);
            pitch = Mth.wrapDegrees(pitch);
            headYRot = Mth.wrapDegrees(headYRot);
        }
    }
    private final WeakHashMap<Entity, Position> lastPositions = new WeakHashMap<>();

    // Local player data
    private WeakReference<LocalPlayer> lastLocalPlayer = null;
    private final List<Object> lastPlayerEntityMeta = new ArrayList<>();
    private final Map<EquipmentSlot, ItemStack> lastPlayerEquipment = new EnumMap<>(EquipmentSlot.class);
    private final ItemStack[] lastHotbarItems = new ItemStack[9];
    private BlockPos lastDestroyPos = null;
    private int lastDestroyProgress = -1;
    private int lastSelectedSlot = -1;
    private float lastExperienceProgress = -1;
    private int lastTotalExperience = -1;
    private int lastExperienceLevel = -1;
    private int lastFoodLevel = -1;
    private float lastSaturationLevel = -1;
    private boolean wasSwinging = false;
    private int lastSwingTime = -1;

    private long lastRtcEpochMilli = 0;
    private boolean forceRtcSync = true;

    private boolean isConfiguring = false;
    private boolean finishedConfiguration = false;
    private boolean finishedPausing = false;
    private ResourceKey<Level> lastDimensionType = null;

    private volatile boolean needsInitialSnapshot = true;
    private volatile boolean closeForWriting = false;
    private volatile boolean isPaused = false;
    private volatile boolean wasPaused = false;
    private volatile boolean skippedPacketDueToWaitingForWrite = false;

    public Recorder(RegistryAccess registryAccess) {
        this.asyncReplaySaver = new AsyncReplaySaver(registryAccess);
        this.gamePacketCodec = PlayPacketCodec.INSTANCE;

        this.metadata.dataVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        this.metadata.protocolVersion = SharedConstants.getProtocolVersion();
        this.metadata.versionString = SharedConstants.getCurrentVersion().getName();

        if (Flashback.isBobbyLoaded) {
            try {
                String bobbyWorldName = FakeChunkManagerAccessor.getCurrentWorldOrServerName(Minecraft.getInstance().getConnection());
                this.metadata.bobbyWorldName = bobbyWorldName;
            } catch (Throwable t) {}
        }

        if (Flashback.supportsDistantHorizons) {
            this.metadata.distantHorizonPaths.putAll(DistantHorizonsSupport.getDimensionPaths());
        }

        this.metadata.namespacesForRegistries = RegistryMetaHelper.calculateNamespacesForRegistries();
        this.metadata.modVersions = ModListHelper.calculateModList();

        String worldName = null;
        ServerData serverData = Minecraft.getInstance().getCurrentServer();
        if (serverData != null) {
            worldName = serverData.name;
            if (worldName.equalsIgnoreCase(I18n.get("selectServer.defaultName"))) {
                worldName = null;
            }
        } else {
            IntegratedServer integratedServer = Minecraft.getInstance().getSingleplayerServer();
            if (integratedServer != null) {
                worldName = integratedServer.worldData.getLevelName();
                if (worldName.equalsIgnoreCase(I18n.get("selectWorld.newWorld"))) {
                    worldName = null;
                }
            }
        }
        this.metadata.worldName = worldName;
    }

    public boolean readyToWrite() {
        return !this.closeForWriting && !this.needsInitialSnapshot && !this.wasPaused;
    }

    public void putDistantHorizonsPaths(Map<String, File> paths) {
        this.metadata.distantHorizonPaths.putAll(paths);
    }

    public void addMarker(ReplayMarker marker) {
        this.metadata.replayMarkers.put(this.writtenTicks, marker);
    }

    public void submitCustomTask(Consumer<ReplayWriter> consumer) {
        if (!this.readyToWrite()) {
            return;
        }

        this.asyncReplaySaver.submit(consumer);
    }

    public void setRegistryAccess(RegistryAccess registryAccess) {
        this.asyncReplaySaver.submit(writer -> writer.setRegistryAccess(registryAccess));
        this.gamePacketCodec = PlayPacketCodec.INSTANCE;
    }

    public String getDebugString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[Flashback] Recording. T: ");
        builder.append(this.writtenTicks);
        builder.append(". S: ");
        builder.append(this.metadata.chunks.size());
        builder.append(" (");
        builder.append(this.writtenTicksInChunk);
        builder.append("/");
        builder.append(CHUNK_LENGTH_SECONDS*20);
        builder.append(")");
        return builder.toString();
    }

    private PositionAndAngle lastPlayerPositionAndAngle = null;
    private float lastPlayerPositionAndAnglePartialTick;
    private final TreeMap<Float, PositionAndAngle> partialPositions = new TreeMap<>();
    private int trackAccuratePositionCounter = 10;

    public void trackPartialPosition(Entity entity, float partialTick) {
        int localPlayerUpdatesPerSecond = Flashback.getConfig().recording.localPlayerUpdatesPerSecond;
        if (localPlayerUpdatesPerSecond <= 20) {
            return;
        }

        double x = Mth.lerp(partialTick, entity.xo, entity.getX());
        double y = Mth.lerp(partialTick, entity.yo, entity.getY());
        double z = Mth.lerp(partialTick, entity.zo, entity.getZ());
        float yaw = entity.getViewYRot(partialTick);
        float pitch = entity.getViewXRot(partialTick);
        this.partialPositions.put(partialTick, new PositionAndAngle(x, y, z, yaw, pitch));
    }

    public void startTick() {
        writeRtc(this.forceRtcSync);
        this.forceRtcSync = false;
    }

    private void writeRtc(boolean force) {
        if (this.needsInitialSnapshot || this.closeForWriting) {
            return;
        }

        Instant now = Instant.now();
        long epochMilli = now.toEpochMilli();

        long predicted = this.lastRtcEpochMilli + 50;
        this.lastRtcEpochMilli = epochMilli;

        long delta = epochMilli - predicted;
        if (!force && delta == 0) {
            return;
        }

        byte byteDelta = (byte) delta;

        if (force || byteDelta != delta) {
            this.asyncReplaySaver.submit(writer -> {
                writer.startAction(ActionRealTimeClock.INSTANCE);
                writer.friendlyByteBuf().writeByte(0);
                writer.friendlyByteBuf().writeLong(epochMilli);
                writer.finishAction(ActionRealTimeClock.INSTANCE);
            });
        } else {
            if (byteDelta == 0) throw new IllegalStateException();
            this.asyncReplaySaver.submit(writer -> {
                writer.startAction(ActionRealTimeClock.INSTANCE);
                writer.friendlyByteBuf().writeByte(byteDelta);
                writer.finishAction(ActionRealTimeClock.INSTANCE);
            });
        }
    }

    public void endTickWithContext(boolean close) {
        this.runWithClientPacketContext(() -> this.endTick(close));
    }

    private void endTick(boolean close) {
        if (this.closeForWriting) {
            return;
        } else if (close) {
            this.closeForWriting = true;
        }

        if (this.isPaused) {
            this.wasPaused = true;
        }

        if (this.needsInitialSnapshot) {
            this.needsInitialSnapshot = false;
            this.writeSnapshot(true);
        }

        this.finishedConfiguration |= this.flushPackets();

        Minecraft minecraft = Minecraft.getInstance();

        boolean isLevelLoaded = !(Minecraft.getInstance().screen instanceof LevelLoadingScreen);
        boolean changedDimensions = false;

        int localPlayerUpdatesPerSecond = Flashback.getConfig().recording.localPlayerUpdatesPerSecond;
        boolean trackAccurateFirstPersonPosition = localPlayerUpdatesPerSecond > 20;
        boolean wroteNewTick = false;

        if (minecraft.level != null && minecraft.getOverlay() == null &&
                !minecraft.isPaused() && !this.isPaused && isLevelLoaded) {
            this.writeEntityPositions();
            this.writeLocalData();

            if (trackAccurateFirstPersonPosition) {
                this.writeAccurateFirstPersonPosition(localPlayerUpdatesPerSecond);
            }

            wroteNewTick = true;
            this.asyncReplaySaver.submit(writer -> writer.startAndFinishAction(ActionNextTick.INSTANCE));
            this.writtenTicksInChunk += 1;
            this.writtenTicks += 1;

            if (this.finishedScreenshot != null) {
                this.asyncReplaySaver.writeIcon(this.finishedScreenshot);
                this.finishedScreenshot = null;
            }
            if (!this.hasTakenScreenshot && ((this.writtenTicks >= 20 && minecraft.screen == null) || close)) {
                this.finishedScreenshot = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
                this.hasTakenScreenshot = true;
            }

            // Write chunk after changing dimensions
            ResourceKey<Level> dimension = minecraft.level.dimension();
            if (this.lastDimensionType == null) {
                this.lastDimensionType = dimension;
            } else if (this.lastDimensionType != dimension) {
                this.lastDimensionType = dimension;
                changedDimensions = true;
            }
        } else if (trackAccurateFirstPersonPosition) {
            this.updateLastPlayerPositionAndAngle(Minecraft.getInstance().player);
            this.partialPositions.clear();
        }

        this.finishedPausing |= this.wasPaused && !this.isPaused;

        boolean writeChunk = close;
        if (minecraft.level != null) {
            boolean finished = this.finishedConfiguration || this.finishedPausing;
            writeChunk |= this.writtenTicksInChunk >= CHUNK_LENGTH_SECONDS*20 || finished || changedDimensions;
        }

        if (writeChunk) {
            // Add an extra tick to avoid edge-cases with 0-length replay chunks
            if (this.writtenTicksInChunk == 0 || !wroteNewTick) {
                this.asyncReplaySaver.submit(writer -> writer.startAndFinishAction(ActionNextTick.INSTANCE));
                this.writtenTicksInChunk += 1;
                this.writtenTicks += 1;
            }

            int chunkId = this.metadata.chunks.size();
            String chunkName = "c" + chunkId + ".flashback";

            if (changedDimensions && Flashback.getConfig().recording.markDimensionChanges) {
                this.addMarker(new ReplayMarker(0xAA00AA, null, "Changed Dimension"));
            }

            var chunkMeta = new FlashbackChunkMeta();
            chunkMeta.duration = this.writtenTicksInChunk;
            this.metadata.chunks.put(chunkName, chunkMeta);
            this.metadata.totalTicks = this.writtenTicks;
            String metadata = GSON.toJson(this.metadata.toJson());

            this.asyncReplaySaver.writeReplayChunk(chunkName, metadata);

            this.writtenTicksInChunk = 0;

            if (!close) {
                // When we finish pausing, we write the snapshot as normal packets directly
                if (this.finishedPausing) {
                    this.asyncReplaySaver.submit(ReplayWriter::startSnapshot);
                    this.asyncReplaySaver.submit(ReplayWriter::endSnapshot);
                    this.writeSnapshot(false);
                } else {
                    this.writeSnapshot(true);
                }
            }

            this.finishedPausing = false;
            this.finishedConfiguration = false;
            if (minecraft.level != null) {
                this.lastDimensionType = minecraft.level.dimension();
            }
        }

        if (!this.isPaused) {
            this.wasPaused = false;
        }
    }

    private void writeAccurateFirstPersonPosition(int localPlayerUpdatesPerSecond) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            this.lastPlayerPositionAndAngle = null;
            this.partialPositions.clear();
            return;
        }

        if (this.lastPlayerPositionAndAngle != null) {
            int divisions = localPlayerUpdatesPerSecond / 20;
            Minecraft.getInstance().mouseHandler.turnPlayer();
            float nextPartialTick = Minecraft.getInstance().getFrameTime();

            double nextX = Mth.lerp(nextPartialTick, player.xo, player.getX());
            double nextY = Mth.lerp(nextPartialTick, player.yo, player.getY());
            double nextZ = Mth.lerp(nextPartialTick, player.zo, player.getZ());
            float nextYaw = player.getViewYRot(nextPartialTick);
            float nextPitch = player.getViewXRot(nextPartialTick);
            PositionAndAngle nextPosition = new PositionAndAngle(nextX, nextY, nextZ, nextYaw, nextPitch);

            if (!this.lastPlayerPositionAndAngle.equals(nextPosition)) {
                this.trackAccuratePositionCounter = 10;
            } else if (this.trackAccuratePositionCounter > 0) {
                this.trackAccuratePositionCounter -= 1;
            }

            if (this.trackAccuratePositionCounter > 0) {
                List<PositionAndAngle> interpolatedPositions = new ArrayList<>();

                for (int i = 0; i <= divisions; i++) {
                    float amount = (float) i / divisions;

                    float floorPartial = -1.0f + this.lastPlayerPositionAndAnglePartialTick;
                    PositionAndAngle floorPosition = this.lastPlayerPositionAndAngle;
                    float ceilPartial = 1.0f + nextPartialTick;
                    PositionAndAngle ceilPosition = nextPosition;

                    Map.Entry<Float, PositionAndAngle> floorEntry = this.partialPositions.floorEntry(amount);
                    Map.Entry<Float, PositionAndAngle> ceilEntry = this.partialPositions.ceilingEntry(Math.nextUp(amount));

                    if (floorEntry != null) {
                        floorPartial = floorEntry.getKey();
                        floorPosition = floorEntry.getValue();
                    }
                    if (ceilEntry != null) {
                        ceilPartial = ceilEntry.getKey();
                        ceilPosition = ceilEntry.getValue();
                    }

                    double lerpAmount = 0.5;
                    if (!Objects.equals(floorPartial, ceilPartial)) {
                        lerpAmount = (amount - floorPartial) / (ceilPartial - floorPartial);
                    }

                    PositionAndAngle interpolatedPosition = floorPosition.lerp(ceilPosition, lerpAmount);
                    interpolatedPositions.add(interpolatedPosition);
                }

                FlashbackAccurateEntityPosition accurateEntityPosition = new FlashbackAccurateEntityPosition(player.getId(), interpolatedPositions);
                this.asyncReplaySaver.submit(writer -> {
                    writer.startAction(ActionAccuratePlayerPosition.INSTANCE);
                    FlashbackAccurateEntityPosition.STREAM_CODEC.encode(writer.friendlyByteBuf(), accurateEntityPosition);
                    writer.finishAction(ActionAccuratePlayerPosition.INSTANCE);
                });
            }
        }

        this.updateLastPlayerPositionAndAngle(player);
        this.partialPositions.clear();
    }

    private void updateLastPlayerPositionAndAngle(@Nullable LocalPlayer player) {
        Map.Entry<Float, PositionAndAngle> floorEntry = this.partialPositions.floorEntry(1.0f);
        if (floorEntry != null) {
            this.lastPlayerPositionAndAngle = floorEntry.getValue();
            this.lastPlayerPositionAndAnglePartialTick = floorEntry.getKey();
        } else if (player != null) {
            double x = player.xo;
            double y = player.yo;
            double z = player.zo;
            float yaw = player.getViewYRot(0.0f);
            float pitch = player.getViewXRot(0.0f);

            this.lastPlayerPositionAndAngle = new PositionAndAngle(x, y, z, yaw, pitch);
            this.lastPlayerPositionAndAnglePartialTick = 1.0f;
        } else {
            this.lastPlayerPositionAndAngle = null;
        }
    }

    public boolean isPaused() {
        return this.isPaused;
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
    }

    public Path finish() {
        return this.asyncReplaySaver.finish();
    }

    private void writeLocalData() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        // Reset local player data if player object changes
        if (this.lastLocalPlayer == null || this.lastLocalPlayer.get() != player) {
            this.resetLastLocalData();
            this.lastLocalPlayer = new WeakReference<>(player);
        }

        List<Packet<? super ClientGamePacketListener>> gamePackets = new ArrayList<>();

        if (Flashback.getConfig().recording.recordHotbar) {
            if (player.experienceProgress != this.lastExperienceProgress || player.totalExperience != this.lastTotalExperience ||
                    player.experienceLevel != this.lastExperienceLevel) {
                this.lastExperienceProgress = player.experienceProgress;
                this.lastTotalExperience = player.totalExperience;
                this.lastExperienceLevel = player.experienceLevel;
                gamePackets.add(new ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
            }

            FoodData foodData = player.getFoodData();
            if (foodData.getFoodLevel() != this.lastFoodLevel || foodData.getSaturationLevel() != this.lastSaturationLevel) {
                this.lastFoodLevel = foodData.getFoodLevel();
                this.lastSaturationLevel = foodData.getSaturationLevel();
                gamePackets.add(new ClientboundSetHealthPacket(player.getHealth(), foodData.getFoodLevel(), foodData.getSaturationLevel()));
            }

            int selectedSlot = player.getInventory().selected;
            if (selectedSlot != this.lastSelectedSlot) {
                gamePackets.add(new ClientboundSetCarriedItemPacket(selectedSlot));
                this.lastSelectedSlot = selectedSlot;
            }
        }

        // Update entity data
        SynchedEntityData.DataItem<?>[] items = Minecraft.getInstance().player.getEntityData().itemsById.values().toArray(SynchedEntityData.DataItem<?>[]::new);
        List<SynchedEntityData.DataValue<?>> changedData = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            SynchedEntityData.DataItem<?> dataItem = items[i];
            Object value = dataItem.value().value();

            if (i >= this.lastPlayerEntityMeta.size()) {
                this.lastPlayerEntityMeta.add(i, value);
            } else {
                Object old = this.lastPlayerEntityMeta.get(i);
                if (!Objects.equals(old, value)) {
                    this.lastPlayerEntityMeta.set(i, value);
                    changedData.add(dataItem.value());
                }
            }
        }

        if (!changedData.isEmpty()) {
            gamePackets.add(new ClientboundSetEntityDataPacket(player.getId(), changedData));
        }

        // Update equipment
        List<Pair<EquipmentSlot, ItemStack>> changedSlots = new ArrayList<>();
        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            ItemStack itemStack = player.getItemBySlot(equipmentSlot);

            if (!this.lastPlayerEquipment.containsKey(equipmentSlot) || !ItemStack.matches(this.lastPlayerEquipment.get(equipmentSlot), itemStack)) {
                ItemStack copied = itemStack.copy();
                this.lastPlayerEquipment.put(equipmentSlot, copied);
                changedSlots.add(Pair.of(equipmentSlot, copied));
            }
        }
        if (!changedSlots.isEmpty()) {
            gamePackets.add(new ClientboundSetEquipmentPacket(player.getId(), changedSlots));
        }

        if (Flashback.getConfig().recording.recordHotbar) {
            for (int i = 0; i < this.lastHotbarItems.length; i++) {
                ItemStack hotbarItem = player.getInventory().getItem(i);

                if (this.lastHotbarItems[i] == null || !ItemStack.matches(this.lastHotbarItems[i], hotbarItem)) {
                    ItemStack copied = hotbarItem.copy();
                    this.lastHotbarItems[i] = copied;
                    gamePackets.add(new ClientboundContainerSetSlotPacket(0, 0, i, copied));
                }
            }
        }

        // Update breaking
        MultiPlayerGameMode multiPlayerGameMode = Minecraft.getInstance().gameMode;
        if (multiPlayerGameMode == null) {
            this.lastDestroyPos = null;
            this.lastDestroyProgress = -1;
        } else {
            BlockPos destroyPos = multiPlayerGameMode.destroyBlockPos.immutable();
            int destroyProgress = multiPlayerGameMode.getDestroyStage();

            boolean changed = destroyProgress != this.lastDestroyProgress;
            if (destroyProgress >= 0) {
                changed |= !destroyPos.equals(this.lastDestroyPos);
            }

            if (changed) {
                gamePackets.add(new ClientboundBlockDestructionPacket(player.getId(), destroyPos, destroyProgress));
            }

            this.lastDestroyPos = destroyPos;
            this.lastDestroyProgress = destroyProgress;
        }

        // Update swinging
        boolean currentSwing = player.swinging;
        int swingTime = player.swingTime;
        if (currentSwing && (!this.wasSwinging || this.lastSwingTime > swingTime)) {
            gamePackets.add(new ClientboundAnimatePacket(player, player.swingingArm == InteractionHand.MAIN_HAND ? 0 : 3));
        }
        this.lastSwingTime = swingTime;
        this.wasSwinging = currentSwing;

        gamePackets.add(new ClientboundSetEntityMotionPacket(player.getId(), player.getDeltaMovement()));

        this.asyncReplaySaver.writeGamePackets(this.gamePacketCodec, gamePackets);
    }

    private void resetLastLocalData() {
        this.lastPlayerEntityMeta.clear();
        this.lastPlayerEquipment.clear();
        this.lastDestroyPos = null;
        this.lastDestroyProgress = -1;
        this.lastSelectedSlot = -1;
        this.lastExperienceProgress = -1;
        this.lastTotalExperience = -1;
        this.lastExperienceLevel = -1;
        this.lastFoodLevel = -1;
        this.lastSaturationLevel = -1;
        Arrays.fill(this.lastHotbarItems, null);
    }

    private void writeEntityPositions() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            this.lastPositions.clear();
            return;
        }

        record IdWithPosition(int id, Position position) {}
        List<IdWithPosition> changedPositions = new ArrayList<>();

        for (Entity entity : level.entitiesForRendering()) {
            if (PacketHelper.shouldIgnoreEntity(entity)) {
                continue;
            }

            Position position;

            float headRot = entity.getYHeadRot();
            if (entity instanceof LivingEntity livingEntity) {
                headRot = livingEntity.lerpHeadSteps > 0 ? (float) livingEntity.lyHeadRot : livingEntity.getYHeadRot();
            }

            var xyz = entity.trackingPosition();
            position = new Position(xyz.x, xyz.y, xyz.z, entity.getYRot(), entity.getXRot(), headRot, entity.onGround());
            Position lastPosition = this.lastPositions.get(entity);

            if (!Objects.equals(position, lastPosition)) {
                this.lastPositions.put(entity, position);
                changedPositions.add(new IdWithPosition(entity.getId(), position));
            }
        }

        if (changedPositions.isEmpty()) {
            return;
        }

        this.asyncReplaySaver.submit(writer -> {
            writer.startAction(ActionMoveEntities.INSTANCE);
            ReplayBuffer friendlyByteBuf = writer.friendlyByteBuf();

            friendlyByteBuf.writeVarInt(1);
            friendlyByteBuf.writeResourceKey(level.dimension());

            friendlyByteBuf.writeVarInt(changedPositions.size());
            for (IdWithPosition changedPosition : changedPositions) {
                friendlyByteBuf.writeVarInt(changedPosition.id);
                friendlyByteBuf.writeDouble(changedPosition.position.x);
                friendlyByteBuf.writeDouble(changedPosition.position.y);
                friendlyByteBuf.writeDouble(changedPosition.position.z);
                friendlyByteBuf.writeFloat(changedPosition.position.yaw);
                friendlyByteBuf.writeFloat(changedPosition.position.pitch);
                friendlyByteBuf.writeFloat(changedPosition.position.headYRot);
                friendlyByteBuf.writeBoolean(changedPosition.position.onGround);
            }

            writer.finishAction(ActionMoveEntities.INSTANCE);
        });
    }

    @SuppressWarnings("unchecked")
    public boolean flushPackets() {
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        boolean joined = false;
        PacketWithPhase pending;
        while ((pending = this.pendingPackets.poll()) != null) {
            if (pending.phase != ConnectionProtocol.PLAY) continue;
            packets.add((Packet<? super ClientGamePacketListener>)pending.packet);
            if (pending.packet instanceof ClientboundLoginPacket) {
                this.asyncReplaySaver.writeGamePackets(this.gamePacketCodec, packets);
                packets.clear();
                this.writeCreateLocalPlayer();
                joined = true;
            }
        }
        if (!packets.isEmpty()) this.asyncReplaySaver.writeGamePackets(this.gamePacketCodec, packets);
        return joined;
    }

    private void writeCreateLocalPlayer() {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            UUID uuid = localPlayer.getUUID();
            double x = localPlayer.getX();
            double y = localPlayer.getY();
            double z = localPlayer.getZ();
            float xRot = localPlayer.getXRot();
            float yRot = localPlayer.getYRot();
            float yHeadRot = localPlayer.getYHeadRot();
            Vec3 deltaMovement = localPlayer.getDeltaMovement();

            GameProfile currentProfile = localPlayer.getGameProfile();

            ImmutableMultimap.Builder<String, Property> propertyMapBuilder = ImmutableMultimap.builder();
            propertyMapBuilder.putAll(Minecraft.getInstance().getUser().getGameProfile().getProperties());
            propertyMapBuilder.putAll(currentProfile.getProperties());
            GameProfile newProfile = new GameProfile(currentProfile.getId(), currentProfile.getName());
            newProfile.getProperties().putAll(propertyMapBuilder.build());
            int gameModeId = Minecraft.getInstance().gameMode.getPlayerMode().getId();

            this.asyncReplaySaver.submit(writer -> {
                writer.startAction(ActionCreateLocalPlayer.INSTANCE);

                ReplayBuffer registryFriendlyByteBuf = writer.friendlyByteBuf();
                registryFriendlyByteBuf.writeUUID(uuid);
                registryFriendlyByteBuf.writeDouble(x);
                registryFriendlyByteBuf.writeDouble(y);
                registryFriendlyByteBuf.writeDouble(z);
                registryFriendlyByteBuf.writeFloat(xRot);
                registryFriendlyByteBuf.writeFloat(yRot);
                registryFriendlyByteBuf.writeFloat(yHeadRot);
                registryFriendlyByteBuf.writeDouble(deltaMovement.x());
                registryFriendlyByteBuf.writeDouble(deltaMovement.y());
                registryFriendlyByteBuf.writeDouble(deltaMovement.z());
                registryFriendlyByteBuf.writeGameProfile(newProfile);
                registryFriendlyByteBuf.writeVarInt(gameModeId);

                writer.finishAction(ActionCreateLocalPlayer.INSTANCE);
            });
        }
    }

    private void runWithClientPacketContext(Runnable runnable) { runnable.run(); }

    public void writeLevelEvent(int type, BlockPos blockPos, int data, boolean globalEvent) {
        if (!this.readyToWrite()) {
            return;
        }

        runWithClientPacketContext(() -> this.pendingPackets.add(new PacketWithPhase(new ClientboundLevelEventPacket(type, blockPos, data, globalEvent), ConnectionProtocol.PLAY)));
    }

    public void writeSound(Holder<SoundEvent> holder, SoundSource soundSource, double x, double y, double z, float volume, float pitch, long seed) {
        if (!this.readyToWrite()) {
            return;
        }

        runWithClientPacketContext(() -> this.pendingPackets.add(new PacketWithPhase(new ClientboundSoundPacket(holder, soundSource, x, y, z, volume, pitch, seed), ConnectionProtocol.PLAY)));
    }

    public void writeEntitySound(Holder<SoundEvent> holder, SoundSource soundSource, Entity entity, float volume, float pitch, long seed) {
        if (!this.readyToWrite()) {
            return;
        }

        runWithClientPacketContext(() -> this.pendingPackets.add(new PacketWithPhase(new ClientboundSoundEntityPacket(holder, soundSource, entity, volume, pitch, seed), ConnectionProtocol.PLAY)));
    }

    public void writePacketAsync(Packet<?> packet, ConnectionProtocol phase) {
        if (!this.readyToWrite()) {
            this.skippedPacketDueToWaitingForWrite = true;
            return;
        }

        if (packet instanceof ClientboundBundlePacket bundlePacket) {
            for (Packet<? super ClientGamePacketListener> subPacket : bundlePacket.subPackets()) {
                this.writePacketAsync(subPacket, phase);
            }
            return;
        }

        // Convert player chat packets into system chat packets
        if (packet instanceof ClientboundPlayerChatPacket playerChatPacket) {
            try {
                Component content = playerChatPacket.unsignedContent() != null ? playerChatPacket.unsignedContent() : Component.literal(playerChatPacket.body().content());
                Component decorated = playerChatPacket.chatType().resolve(Minecraft.getInstance().level.registryAccess()).orElseThrow().decorate(content);
                packet = new ClientboundSystemChatPacket(decorated, false);
            } catch (Exception e) {
                return;
            }
        }

        // Don't save fabric-screen-handler-api packets
        if (packet instanceof ClientboundCustomPayloadPacket customPayloadPacket) {
            if (customPayloadPacket.getIdentifier().getNamespace().startsWith("fabric-screen-handler-api")) {
                return;
            }
        }

        if (IgnoredPacketSet.isIgnored(packet)) {
            return;
        }

        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            try {
                int localPlayerId = localPlayer.getId();
                if (packet instanceof ClientboundSetEntityDataPacket entityDataPacket && entityDataPacket.id() == localPlayerId) {
                    return;
                }
                if (packet instanceof ClientboundSetEquipmentPacket entityEquipmentPacket && entityEquipmentPacket.getEntity() == localPlayerId) {
                    return;
                }
            } catch (Exception ignored) {} // getId can throw if id hasn't been assigned
        }

        if (packet instanceof ClientboundCustomPayloadPacket customPayload) {
            packet = com.moulberry.flashback.packet.RecordedCustomPayload.copyOf(customPayload);
        }
        this.pendingPackets.add(new PacketWithPhase(packet, phase));
    }

    public void writeSnapshot(boolean asActualSnapshot) {
        Minecraft minecraft = Minecraft.getInstance();

        if (this.skippedPacketDueToWaitingForWrite) {
            // If we skipped a packet, we should run all updates to ensure that
            // the packet changes have been applied to the game state.
            this.skippedPacketDueToWaitingForWrite = false;
            minecraft.runAllTasks();
        }

        if (asActualSnapshot) {
            this.asyncReplaySaver.submit(ReplayWriter::startSnapshot);
        }

        if (this.lastRtcEpochMilli == 0) {
            this.writeRtc(true);
        } else {
            this.forceRtcSync = true;
        }

        ClientLevel level = minecraft.level;
        LocalPlayer localPlayer = minecraft.player;
        ClientPacketListener connection = minecraft.getConnection();
        MultiPlayerGameMode gameMode = minecraft.gameMode;
        ClientChunkCache clientChunkCache = level.getChunkSource();

        AtomicReferenceArray<LevelChunk> chunksList = clientChunkCache.storage.chunks;

        // 1.20.1 carries dynamic registries directly in Login, before PLAY tags/features.
        List<Packet<? super ClientGamePacketListener>> gamePackets = new ArrayList<>();
        long hashedSeed = level.getBiomeManager().biomeZoomSeed;
        var loginPacket = new ClientboundLoginPacket(localPlayer.getId(), level.getLevelData().isHardcore(),
            gameMode.getPlayerMode(), gameMode.getPreviousPlayerMode(), connection.levels(), connection.registryAccess().freeze(),
            level.dimensionTypeId(), level.dimension(), hashedSeed, 1, minecraft.options.getEffectiveRenderDistance(),
            level.getServerSimulationDistance(), localPlayer.isReducedDebugInfo(), localPlayer.shouldShowDeathScreen(),
            level.isDebug(), level.getLevelData().isFlat, localPlayer.getLastDeathLocation(), 0);
        gamePackets.add(loginPacket);
        gamePackets.add(new ClientboundUpdateEnabledFeaturesPacket(FeatureFlags.REGISTRY.toNames(level.enabledFeatures())));
        Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> serializedTags = new HashMap<>();
        level.registryAccess().registries().forEach(entry -> serializedTags.put(entry.key(), TagNetworkSerialization.serializeToNetwork(entry.value())));
        gamePackets.add(new ClientboundUpdateTagsPacket(serializedTags));
        ClientboundResourcePackPacket resourcePack = com.moulberry.flashback.record.RecordedResourcePack.current();
        if (resourcePack != null) gamePackets.add(resourcePack);

        // Write local player
        this.asyncReplaySaver.writeGamePackets(this.gamePacketCodec, gamePackets);
        gamePackets.clear();
        this.writeCreateLocalPlayer();

        // Create player info update packet
        var infoUpdatePacket = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of());
        infoUpdatePacket.entries = new ArrayList<>();
        Set<UUID> addedEntries = new HashSet<>();
        Set<UUID> addedWithValidProperties = new HashSet<>();
        for (PlayerInfo info : connection.getListedOnlinePlayers()) {
            boolean isValidProperties = !info.getProfile().getProperties().isEmpty();
            if (addedEntries.add(info.getProfile().getId())) {
                infoUpdatePacket.entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(info.getProfile().getId(),
                    info.getProfile(), true, info.getLatency(), info.getGameMode(), info.getTabListDisplayName(), null));
                if (isValidProperties) {
                    addedWithValidProperties.add(info.getProfile().getId());
                }
            }
        }
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            boolean isValidProperties = !info.getProfile().getProperties().isEmpty();
            boolean add = false;
            if (addedEntries.add(info.getProfile().getId())) {
                add = true;
            } else if (isValidProperties && !addedWithValidProperties.contains(info.getProfile().getId())) {
                infoUpdatePacket.entries.removeIf(entry -> entry.profileId().equals(info.getProfile().getId()));
                add = true;
            }
            if (add) {
                infoUpdatePacket.entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(info.getProfile().getId(),
                    info.getProfile(), false, info.getLatency(), info.getGameMode(), info.getTabListDisplayName(), null));
                if (isValidProperties) {
                    addedWithValidProperties.add(info.getProfile().getId());
                }
            }
        }
        for (AbstractClientPlayer player : level.players()) {
            PlayerInfo info = player.getPlayerInfo();
            if (info != null) {
                boolean isValidProperties = !info.getProfile().getProperties().isEmpty();
                boolean add = false;
                if (addedEntries.add(info.getProfile().getId())) {
                    add = true;
                } else if (isValidProperties && !addedWithValidProperties.contains(info.getProfile().getId())) {
                    infoUpdatePacket.entries.removeIf(entry -> entry.profileId().equals(info.getProfile().getId()));
                    add = true;
                }
                if (add) {
                    infoUpdatePacket.entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(player.getUUID(),
                        player.getGameProfile(), false, info.getLatency(), info.getGameMode(), info.getTabListDisplayName(), null));
                    if (isValidProperties) {
                        addedWithValidProperties.add(info.getProfile().getId());
                    }
                }
            } else if (addedEntries.add(player.getUUID())) {
                infoUpdatePacket.entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(player.getUUID(),
                    player.getGameProfile(), false, 0, GameType.DEFAULT_MODE, player.getDisplayName(), null));
            }
        }
        gamePackets.add(infoUpdatePacket);

        // Tab list
        PlayerTabOverlay playerTabOverlay = minecraft.gui.getTabList();
        gamePackets.add(new ClientboundTabListPacket(
            playerTabOverlay.header != null ? playerTabOverlay.header : Component.empty(),
            playerTabOverlay.footer != null ? playerTabOverlay.footer : Component.empty()
        ));

        // Boss bar
        BossHealthOverlay bossOverlay = minecraft.gui.getBossOverlay();
        for (LerpingBossEvent event : bossOverlay.events.values()) {
            gamePackets.add(ClientboundBossEventPacket.createAddPacket(event));
        }

        // Scoreboard
        Scoreboard scoreboard = level.getScoreboard();
        for (PlayerTeam playerTeam : scoreboard.getPlayerTeams()) {
            gamePackets.add(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(playerTeam, true));
        }
        HashSet<Objective> handledObjectives = new HashSet<>();
        for (int displaySlot = 0; displaySlot < 19; displaySlot++) {
            Objective objective = scoreboard.getDisplayObjective(displaySlot);
            if (objective != null && handledObjectives.add(objective)) {
                gamePackets.add(new ClientboundSetObjectivePacket(objective, 0));

                for (int displaySlot2 = 0; displaySlot2 < 19; displaySlot2++) {
                    if (scoreboard.getDisplayObjective(displaySlot2) == objective) {
                        gamePackets.add(new ClientboundSetDisplayObjectivePacket(displaySlot2, objective));
                    }
                }

                for (Score playerScoreEntry : scoreboard.getPlayerScores(objective)) {
                    gamePackets.add(new ClientboundSetScorePacket(net.minecraft.server.ServerScoreboard.Method.CHANGE, objective.getName(), playerScoreEntry.getOwner(), playerScoreEntry.getScore()));
                }
            }
        }

        // Level info
        WorldBorder worldBorder = level.getWorldBorder();
        gamePackets.add(new ClientboundInitializeBorderPacket(worldBorder));
        gamePackets.add(new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)));
        gamePackets.add(new ClientboundSetDefaultSpawnPositionPacket(level.getSharedSpawnPos(), level.getSharedSpawnAngle()));
        if (level.isRaining()) {
            gamePackets.add(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0f));
        } else {
            gamePackets.add(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0f));
        }
        gamePackets.add(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.getRainLevel(1.0f)));
        gamePackets.add(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.getThunderLevel(1.0f)));

        // Force light updates
        try {
            while (true) {
                Runnable runnable = level.lightUpdateQueue.poll();
                if (runnable == null) {
                    break;
                } else {
                    runnable.run();
                }
            }
        } catch (Exception e) {
            Flashback.LOGGER.error("Error while running light tasks", e);
        }
        for (int i = 0; i < 256; i++) {
            try {
                if (level.getLightEngine().runLightUpdates() == 0) {
                    break;
                }
            } catch (Exception e) {
                Flashback.LOGGER.error("Error while running light updates", e);
                break;
            }
        }

        writeChunkDataSnapshot(chunksList, clientChunkCache, level, localPlayer, gamePackets);

        if (Flashback.getConfig().recording.recordHotbar) {
            this.lastExperienceProgress = localPlayer.experienceProgress;
            this.lastTotalExperience = localPlayer.totalExperience;
            this.lastExperienceLevel = localPlayer.experienceLevel;
            gamePackets.add(new ClientboundSetExperiencePacket(localPlayer.experienceProgress, localPlayer.totalExperience, localPlayer.experienceLevel));

            FoodData foodData = localPlayer.getFoodData();
            this.lastFoodLevel = foodData.getFoodLevel();
            this.lastSaturationLevel = foodData.getSaturationLevel();
            gamePackets.add(new ClientboundSetHealthPacket(localPlayer.getHealth(), foodData.getFoodLevel(), foodData.getSaturationLevel()));

            int selectedSlot = localPlayer.getInventory().selected;
            this.lastSelectedSlot = selectedSlot;
            gamePackets.add(new ClientboundSetCarriedItemPacket(selectedSlot));

            for (int i = 0; i < 9; i++) {
                ItemStack hotbarItem = localPlayer.getInventory().getItem(i);
                this.lastHotbarItems[i] = hotbarItem.copy();
                gamePackets.add(new ClientboundContainerSetSlotPacket(0, 0, i, hotbarItem.copy()));
            }
        }

        // Entity data
        for (Entity entity : level.entitiesForRendering()) {
            if (PacketHelper.shouldIgnoreEntity(entity)) {
                continue;
            }

            if (!(entity instanceof LocalPlayer)) {
                gamePackets.add(PacketHelper.createAddEntity(entity));
            }

            List<SynchedEntityData.DataValue<?>> nonDefaultEntityData = entity.getEntityData().getNonDefaultValues();
            if (nonDefaultEntityData != null && !nonDefaultEntityData.isEmpty()) {
                gamePackets.add(new ClientboundSetEntityDataPacket(entity.getId(), nonDefaultEntityData));
            }

            if (entity instanceof LivingEntity livingEntity) {
                Collection<AttributeInstance> syncableAttributes = livingEntity.getAttributes().getSyncableAttributes();
                if (!syncableAttributes.isEmpty()) {
                    gamePackets.add(new ClientboundUpdateAttributesPacket(entity.getId(), syncableAttributes));
                }

                List<Pair<EquipmentSlot, ItemStack>> changedSlots = new ArrayList<>();
                for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                    ItemStack itemStack = livingEntity.getItemBySlot(equipmentSlot);
                    if (!itemStack.isEmpty()) {
                        changedSlots.add(Pair.of(equipmentSlot, itemStack.copy()));
                    }
                }
                if (!changedSlots.isEmpty()) {
                    gamePackets.add(new ClientboundSetEquipmentPacket(entity.getId(), changedSlots));
                }
            }

            if (entity.isVehicle()) {
                gamePackets.add(new ClientboundSetPassengersPacket(entity));
            }
            if (entity.isPassenger()) {
                gamePackets.add(new ClientboundSetPassengersPacket(entity.getVehicle()));
            }

            if (entity instanceof Mob leashable && leashable.isLeashed()) {
                gamePackets.add(new ClientboundSetEntityLinkPacket(entity, leashable.getLeashHolder()));
            }
        }

        // Map data
        for (Map.Entry<String, MapItemSavedData> entry : level.mapData.entrySet()) {
            MapItemSavedData data = entry.getValue();

            int offsetX = 0;
            int offsetY = 0;
            int sizeX = 128;
            int sizeY = 128;

            if (data.colors.length != sizeX * sizeY) {
                Flashback.LOGGER.error("Unable to save snapshot of map data, expected colour array to be size {}, got {} instead", sizeX * sizeY, data.colors.length);
                continue;
            }

            byte[] colorsCopy = new byte[sizeX * sizeY];
            System.arraycopy(data.colors, 0, colorsCopy, 0, sizeX * sizeY);
            MapItemSavedData.MapPatch patch = new MapItemSavedData.MapPatch(offsetX, offsetY, sizeX, sizeY, colorsCopy);

            ArrayList<MapDecoration> decorations = new ArrayList<>();
            for (MapDecoration decoration : data.getDecorations()) {
                decorations.add(decoration);
            }

            var packet = new ClientboundMapItemDataPacket(Integer.parseInt(entry.getKey().substring("map_".length())), data.scale, data.locked, decorations, patch);
            gamePackets.add(packet);
        }

        writeCustomSnapshot(gamePackets::add);

        this.asyncReplaySaver.writeGamePackets(this.gamePacketCodec, gamePackets);

        if (asActualSnapshot) {
            this.asyncReplaySaver.submit(ReplayWriter::endSnapshot);
        }
    }

    private void writeChunkDataSnapshot(AtomicReferenceArray<LevelChunk> chunksList, ClientChunkCache clientChunkCache, ClientLevel level, LocalPlayer localPlayer, List<Packet<? super ClientGamePacketListener>> gamePackets) {
        // Generate the list of chunks we need to save
        List<LevelChunk> chunks = new ArrayList<>(chunksList.length());
        LongOpenHashSet seenChunkPositions = new LongOpenHashSet(chunksList.length());
        for (int i = 0; i < chunksList.length(); i++) {
            LevelChunk chunk = chunksList.get(i);
            if (chunk != null) {
                chunks.add(chunk);
                seenChunkPositions.add(chunk.getPos().toLong());
            }
        }

        if (Flashback.isBobbyLoaded && Flashback.getConfig().recording.recordBobbyIntoReplay) {
            BobbyUtil.addBobbyChunks(clientChunkCache, chunks, seenChunkPositions);
        }

        if (Runtime.getRuntime().availableProcessors() <= 1) {
            List<ClientboundLevelChunkWithLightPacket> levelChunkPackets = new ArrayList<>();

            for (var chunk : chunks) {
                levelChunkPackets.add(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null));
            }

            int centerX = localPlayer.getBlockX() >> 4;
            int centerZ = localPlayer.getBlockZ() >> 4;
            levelChunkPackets.sort(Comparator.comparingInt(task -> {
                int dx = task.getX() - centerX;
                int dz = task.getZ() - centerZ;
                return dx*dx + dz*dz;
            }));

            gamePackets.addAll(levelChunkPackets);
        } else {
            ForkJoinPool pool = new ForkJoinPool();
            try {
                final class PositionedTask {
                    private final ChunkPos pos;
                    private final ForkJoinTask<ClientboundLevelChunkWithLightPacket> task;
                    private ClientboundLightUpdatePacketData lightData = null;

                    PositionedTask(ChunkPos pos, ForkJoinTask<ClientboundLevelChunkWithLightPacket> task) {
                        this.pos = pos;
                        this.task = task;
                    }
                }
                List<PositionedTask> levelChunkPacketTasks = new ArrayList<>();

                for (var chunk : chunks) {
                    var task = pool.submit(() -> new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), new BitSet(), new BitSet()));
                    levelChunkPacketTasks.add(new PositionedTask(chunk.getPos(), task));
                }

                int centerX = localPlayer.getBlockX() >> 4;
                int centerZ = localPlayer.getBlockZ() >> 4;
                levelChunkPacketTasks.sort(Comparator.comparingInt(task -> {
                    int dx = task.pos.x - centerX;
                    int dz = task.pos.z - centerZ;
                    return dx*dx + dz*dz;
                }));

                // We get the light data on this thread to avoid slowdown due to synchronization
                for (PositionedTask positionedTask : levelChunkPacketTasks) {
                    positionedTask.lightData = new ClientboundLightUpdatePacketData(positionedTask.pos, level.getLightEngine(), null, null);
                }

                for (PositionedTask positionedTask : levelChunkPacketTasks) {
                    ClientboundLevelChunkWithLightPacket levelChunkWithLightPacket = positionedTask.task.join();
                    levelChunkWithLightPacket.lightData = positionedTask.lightData;
                    gamePackets.add(levelChunkWithLightPacket);
                }
            } finally { pool.shutdown(); }
        }
    }

    public void writeCustomSnapshot(Consumer<Packet<? super ClientGamePacketListener>> consumer) {
        // Mods can mixin here if they want to add custom actions (using this.asyncReplayServer) or packets
    }

}
