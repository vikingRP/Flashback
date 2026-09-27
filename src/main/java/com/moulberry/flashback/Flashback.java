package com.moulberry.flashback;

import com.moulberry.flashback.platform.ForgePlatform;
import com.moulberry.flashback.packet.FlashbackNetworking;
import com.moulberry.flashback.packet.PacketCodec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.serialization.Lifecycle;
import com.moulberry.flashback.action.*;
import com.moulberry.flashback.combo_options.MarkerColour;
import com.moulberry.flashback.command.BetterColorArgument;
import com.moulberry.flashback.compat.DistantHorizonsSupport;
import com.moulberry.flashback.compat.simple_voice_chat.SimpleVoiceChatPlayback;
import com.moulberry.flashback.configuration.FlashbackConfigV1;
import com.moulberry.flashback.editor.keybinds.Keybinds;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.taskbar.TaskbarManager;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.keyframe.KeyframeRegistry;
import com.moulberry.flashback.keyframe.types.*;
import com.moulberry.flashback.packet.FlashbackAccurateEntityPosition;
import com.moulberry.flashback.packet.FlashbackClearEntities;
import com.moulberry.flashback.packet.FlashbackClearParticles;
import com.moulberry.flashback.packet.FinishedServerTick;
import com.moulberry.flashback.packet.FlashbackForceClientTick;
import com.moulberry.flashback.packet.FlashbackInstantlyLerp;
import com.moulberry.flashback.packet.FlashbackRawCustomPayload;
import com.moulberry.flashback.packet.FlashbackRemoteExperience;
import com.moulberry.flashback.packet.FlashbackRemoteFoodData;
import com.moulberry.flashback.packet.FlashbackRemoteSelectHotbarSlot;
import com.moulberry.flashback.packet.FlashbackRemoteSetSlot;
import com.moulberry.flashback.packet.FlashbackSetBorderLerpStartTime;
import com.moulberry.flashback.packet.FlashbackVoiceChatSound;
import com.moulberry.flashback.playback.EmptyLevelSource;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.flashback.record.Recorder;
import com.moulberry.flashback.record.ReplayExporter;
import com.moulberry.flashback.record.ReplayMarker;
import com.moulberry.flashback.screen.RecoverRecordingsScreen;
import com.moulberry.flashback.screen.SaveReplayScreen;
import com.moulberry.flashback.screen.UnsupportedLoaderScreen;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.utils.AsyncFileDialogs;
import com.moulberry.flashback.visuals.AccurateEntityPositionHandler;
import com.moulberry.lattice.Lattice;
import com.moulberry.lattice.element.LatticeElements;
import com.seibel.distanthorizons.api.DhApi;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.*;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public class Flashback {
    public static final Logger LOGGER = LoggerFactory.getLogger("flashback");

    public static final int MAGIC = 0xD780E884;
    public static volatile Recorder RECORDER = null;
    public static ExportJob EXPORT_JOB = null;
    private static FlashbackConfigV1 config;
    public static LatticeElements configElements = null;
    private static Path configDirectory = null;

    private static int delayedStartRecording = 0;
    private static boolean delayedOpenConfig = false;
    private static volatile boolean isInReplay = false;

    public static boolean supportsDistantHorizons = false;

    public static boolean isBobbyLoaded = false;

    private static final List<Path> pendingReplaySave = new ArrayList<>();
    private static final List<Path> pendingReplayRecovery = new ArrayList<>();
    private static List<String> pendingUnsupportedModsForRecording = null;

    private static boolean isOpeningReplay = false;

    public static long worldBorderLerpStartTime = -1L;

    private static final String category = "key.category.flashback.keybind";
    public static final KeyMapping createMarker1KeyBind = new KeyMapping("flashback.keybind.create_marker_1",
        InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category);
    public static final KeyMapping createMarker2KeyBind = new KeyMapping("flashback.keybind.create_marker_2",
        InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category);
    public static final KeyMapping createMarker3KeyBind = new KeyMapping("flashback.keybind.create_marker_3",
        InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category);
    public static final KeyMapping createMarker4KeyBind = new KeyMapping("flashback.keybind.create_marker_4",
        InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category);

    public static final ResourceLocation RECORDING_INFO_DEBUG_SCREEN_ID = createIdentifier("recording_info");

    public static ResourceLocation createIdentifier(String value) {
        return new ResourceLocation("flashback", value);
    }

    public static Path getDataDirectory() {
        return ForgePlatform.getInstance().getGameDir().resolve("flashback");
    }

    public static Path getReplayFolder() {
        return Flashback.getDataDirectory().resolve("replays");
    }

    public static Path getConfigDirectory() {
        if (configDirectory == null) {
            configDirectory = ForgePlatform.getInstance().getConfigDir().resolve("flashback");
            try {
                Files.createDirectories(configDirectory);
            } catch (Exception e) {
                LOGGER.error("Unable to create directories for config folder", e);
            }
        }
        return configDirectory;
    }

    public void onInitializeClient() {
        FlashbackNetworking.register(FinishedServerTick.TYPE, PacketCodec.unit(FinishedServerTick.INSTANCE));
        Path configFolder = ForgePlatform.getInstance().getConfigDir().resolve("flashback");

        try {
            Files.createDirectories(configFolder);
        } catch (IOException e) {
            Flashback.LOGGER.error("Failed to create config folder", e);
        }

        config = getConfig();
        com.moulberry.flashback.exporting.NativeLibraryBootstrap.initialize(
            ForgePlatform.getInstance().getGameDir().resolve("flashback/native-cache"),
            config.exporting.useSystemFFmpeg);
        configElements = LatticeElements.fromAnnotations(FlashbackTextComponents.FLASHBACK_OPTIONS, config);

        if (config.exporting.useSystemFFmpeg) {
            System.setProperty("org.bytedeco.javacpp.pathsfirst", "true");
        }
        if (config.internal.nfdUsePortal) {
            System.setProperty("org.lwjgl.nfd.linux.portal", "true");
        }

        if (ForgePlatform.getInstance().isDevelopmentEnvironment()) {
            Minecraft.getInstance().execute(() -> Lattice.performTest(configElements));
        }

        Keybinds.load(config);
        TempFolderProvider.tryDeleteStaleFolders(TempFolderProvider.TempFolderType.SERVER);

        Path recordingFolder = TempFolderProvider.getTypedTempFolder(TempFolderProvider.TempFolderType.RECORDING);
        if (Files.exists(recordingFolder)) {
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(recordingFolder)) {
                Iterator<Path> iterator = directoryStream.iterator();
                while (iterator.hasNext()) {
                    Path folder = iterator.next();

                    if (Files.exists(folder.resolve("metadata.json"))) {
                        pendingReplayRecovery.add(folder);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (pendingReplayRecovery.isEmpty()) {
            TempFolderProvider.tryDeleteStaleFolders(TempFolderProvider.TempFolderType.RECORDING);
        }

        // Delete partial exports
        try {
            FileUtils.deleteDirectory(Path.of("replay_export_temp").toFile());
        } catch (Exception ignored) {}

        this.deleteUnusedReplayStates();

        ActionRegistry.register(ActionNextTick.INSTANCE);
        ActionRegistry.register(ActionGamePacket.INSTANCE);
        ActionRegistry.register(ActionConfigurationPacket.INSTANCE);
        ActionRegistry.register(ActionCreateLocalPlayer.INSTANCE);
        ActionRegistry.register(ActionMoveEntities.INSTANCE);
        ActionRegistry.register(ActionLevelChunkCached.INSTANCE);
        ActionRegistry.register(ActionAccuratePlayerPosition.INSTANCE);
        ActionRegistry.register(ActionRealTimeClock.INSTANCE);

        KeyframeRegistry.register(CameraKeyframeType.INSTANCE);
        KeyframeRegistry.register(CameraOrbitKeyframeType.INSTANCE);
        KeyframeRegistry.register(TrackEntityKeyframeType.INSTANCE);
        KeyframeRegistry.register(CameraShakeKeyframeType.INSTANCE);
        KeyframeRegistry.register(FOVKeyframeType.INSTANCE);
        KeyframeRegistry.register(SpeedKeyframeType.INSTANCE);
        KeyframeRegistry.register(TimelapseKeyframeType.INSTANCE);
        KeyframeRegistry.register(TimeOfDayKeyframeType.INSTANCE);
        KeyframeRegistry.register(FreezeKeyframeType.INSTANCE);
        KeyframeRegistry.register(BlockOverrideKeyframeType.INSTANCE);
        KeyframeRegistry.register(AudioKeyframeType.INSTANCE);

        FlashbackNetworking.register(FlashbackForceClientTick.TYPE, PacketCodec.unit(FlashbackForceClientTick.INSTANCE), (payload, client) -> {
            if (Flashback.isInReplay()) {
                Minecraft.getInstance().tick();
            }
        });

        FlashbackNetworking.register(FlashbackClearParticles.TYPE, PacketCodec.unit(FlashbackClearParticles.INSTANCE), (payload, client) -> {
            if (Flashback.isInReplay()) {
                Minecraft.getInstance().particleEngine.clearParticles();
            }
        });

        FlashbackNetworking.register(FlashbackClearEntities.TYPE, PacketCodec.unit(FlashbackClearEntities.INSTANCE), (payload, client) -> {
            if (Flashback.isInReplay()) {
                for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
                    if (entity != null && !(entity instanceof Player)) {
                        entity.discard();
                    }
                }
            }
        });

        FlashbackNetworking.register(FlashbackInstantlyLerp.TYPE, PacketCodec.unit(FlashbackInstantlyLerp.INSTANCE), (payload, client) -> {
            if (Flashback.isInReplay()) {
                for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
                    if (entity != client.player) {
                        com.moulberry.flashback.playback.ReplayInterpolation.finish(entity);
                    }
                }
            }
        });

        FlashbackNetworking.register(FlashbackRemoteSelectHotbarSlot.TYPE, FlashbackRemoteSelectHotbarSlot.STREAM_CODEC, (payload, client) -> {
            if (Flashback.isInReplay()) {
                Entity entity = Minecraft.getInstance().level.getEntity(payload.entityId());
                if (entity instanceof Player player) {
                    player.getInventory().selected = payload.slot();
                }
            }
        });

        FlashbackNetworking.register(FlashbackRemoteExperience.TYPE, FlashbackRemoteExperience.STREAM_CODEC, (payload, client) -> {
            if (Flashback.isInReplay()) {
                Entity entity = Minecraft.getInstance().level.getEntity(payload.entityId());
                if (entity instanceof Player player) {
                    player.experienceProgress = payload.experienceProgress();
                    player.totalExperience = payload.totalExperience();
                    player.experienceLevel = payload.experienceLevel();
                }
            }
        });

        FlashbackNetworking.register(FlashbackRemoteFoodData.TYPE, FlashbackRemoteFoodData.STREAM_CODEC, (payload, client) -> {
            if (Flashback.isInReplay()) {
                Entity entity = Minecraft.getInstance().level.getEntity(payload.entityId());
                if (entity instanceof Player player) {
                    player.getFoodData().setFoodLevel(payload.foodLevel());
                    player.getFoodData().setSaturation(payload.saturationLevel());
                }
            }
        });

        FlashbackNetworking.register(FlashbackRemoteSetSlot.TYPE, FlashbackRemoteSetSlot.STREAM_CODEC, (payload, client) -> {
            if (Flashback.isInReplay()) {
                Entity entity = Minecraft.getInstance().level.getEntity(payload.entityId());
                if (entity instanceof Player player) {
                    player.getInventory().setItem(payload.slot(), payload.itemStack());
                }
            }
        });

        if (ForgePlatform.getInstance().isModLoaded("voicechat")) {
            FlashbackNetworking.register(FlashbackVoiceChatSound.TYPE, FlashbackVoiceChatSound.STREAM_CODEC, (payload, client) -> {
                if (Flashback.isInReplay()) {
                    SimpleVoiceChatPlayback.play(payload);
                }
            });
        }

        FlashbackNetworking.register(FlashbackAccurateEntityPosition.TYPE, FlashbackAccurateEntityPosition.STREAM_CODEC, (payload, client) -> {
            if (Flashback.isInReplay()) {
                AccurateEntityPositionHandler.update(payload);
            }
        });

        FlashbackNetworking.register(FlashbackSetBorderLerpStartTime.TYPE, FlashbackSetBorderLerpStartTime.STREAM_CODEC, (payload, client) -> {
            if (Flashback.isInReplay()) {
                worldBorderLerpStartTime = payload.time();
            }
        });

        FlashbackNetworking.register(FlashbackRawCustomPayload.TYPE, FlashbackRawCustomPayload.STREAM_CODEC, (payload, client) -> {
            if (Flashback.isInReplay()) {
                var connection = client.getConnection();
                if (connection == null || payload.configPhase()) return;
                var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.packetBytes()));
                try {
                    // The packet copies its body and releases that copy after dispatch.
                    new ClientboundCustomPayloadPacket(buffer).handle(connection);
                } finally {
                    buffer.release();
                }
            }
        });

        // Recording debug text is added by MixinDebugScreenOverlay on 1.20.1.

        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> {
            var dispatcher = event.getDispatcher();
            var flashback = Commands.literal("flashback");
            flashback.then(Commands.literal("start").executes(this::startRecordingReplay));
            flashback.then(Commands.literal("finish").executes(this::finishRecordingReplay));
            flashback.then(Commands.literal("end").executes(this::finishRecordingReplay));
            flashback.then(Commands.literal("pause").executes(ctx -> {
                pauseRecordingReplay(true);
                return 0;
            }));
            flashback.then(Commands.literal("unpause").executes(ctx -> {
                pauseRecordingReplay(false);
                return 0;
            }));
            flashback.then(Commands.literal("config").executes(this::openFlashbackConfig));
            flashback.then(Commands.literal("mark")
                .executes(command -> {
                    this.addMarker(null, null, null);
                    return 0;
                }).then(Commands.argument("color", BetterColorArgument.color()).executes(command -> {
                    int colour = command.getArgument("color", Integer.class);
                    this.addMarker(colour, null, null);
                    return 0;
                }).then(Commands.argument("savePosition", BoolArgumentType.bool()).executes(command -> {
                    int colour = command.getArgument("color", Integer.class);
                    boolean savePosition = command.getArgument("savePosition", Boolean.class);
                    this.addMarker(colour, savePosition, null);
                    return 0;
                }).then(Commands.argument("description", StringArgumentType.greedyString()).executes(command -> {
                    int colour = command.getArgument("color", Integer.class);
                    boolean savePosition = command.getArgument("savePosition", Boolean.class);
                    String description = command.getArgument("description", String.class);
                    this.addMarker(colour, savePosition, description);
                    return 0;
                })))));
            dispatcher.register(flashback);
        });

        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> {
            var dispatcher = event.getDispatcher();
            if (!Flashback.isInReplay() && !isOpeningReplay) {
                return;
            }

            String hideName = "hide";
            if (dispatcher.findNode(Collections.singleton("hide")) != null) {
                hideName = "hide_flashback";
            }
            var hideEntity = Commands.literal(hideName).then(Commands.argument("targets", EntityArgument.entities()).executes(command -> {
                EditorState editorState = EditorStateManager.getCurrent();
                if (!Flashback.isInReplay() || editorState == null) {
                    command.getSource().sendFailure(Component.translatable("flashback.command_only_inside_replay", Component.literal("hide")));
                    return 0;
                }
                var entities = EntityArgument.getEntities(command, "targets");

                for (Entity entity : entities) {
                    editorState.hideDuringExport.add(entity.getUUID());
                }

                int count = entities.size();
                command.getSource().sendSuccess(() -> Component.translatable("flashback.hide_command.n_entities_hidden", Component.literal(String.valueOf(count))), false);
                return 0;
            }));
            dispatcher.register(hideEntity);

            String showName = "show";
            if (dispatcher.findNode(Collections.singleton("show")) != null) {
                showName = "show_flashback";
            }
            var showEntity = Commands.literal(showName).then(Commands.argument("targets", EntityArgument.entities()).executes(command -> {
                EditorState editorState = EditorStateManager.getCurrent();
                if (!Flashback.isInReplay() || editorState == null) {
                    command.getSource().sendFailure(Component.translatable("flashback.command_only_inside_replay", Component.literal("show")));
                    return 0;
                }
                var entities = EntityArgument.getEntities(command, "targets");

                for (Entity entity : entities) {
                    editorState.hideDuringExport.remove(entity.getUUID());
                }

                int count = entities.size();
                command.getSource().sendSuccess(() -> Component.translatable("flashback.show_command.n_entities_shown", Component.literal(String.valueOf(count))), false);
                return 0;
            }));
            dispatcher.register(showEntity);
        });

        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            if (!Flashback.isInReplay() && Flashback.getConfig().recordingControls.automaticallyStart && RECORDER == null) {
                delayedStartRecording = 20;
            }
            if (ForgePlatform.getInstance().isModLoaded("voicechat")) {
                SimpleVoiceChatPlayback.cleanUp();
            }
        });

        AtomicReference<String> unsupportedLoader = new AtomicReference<>(findUnsupportedLoaders());

        AtomicBoolean synchronizeTickingCanTickClient = new AtomicBoolean(true);
        AtomicBoolean synchronizeTickingCanTickServer = new AtomicBoolean(true);

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft minecraft = Minecraft.getInstance();
            updateIsInReplay();

            AccurateEntityPositionHandler.tick();

            // Fix for camera entity sometimes being incorrect when respawning
            Entity camera = Minecraft.getInstance().getCameraEntity();
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && camera != null && camera != player) {
                if (camera.isRemoved()) {
                    Entity other = player.level().getEntity(camera.getId());
                    if (other != null && !other.isRemoved()) {
                        Minecraft.getInstance().setCameraEntity(other);
                    }
                }
            }

            Flashback.getConfig().tickDelayedSave();

            synchronizeTickingCanTickServer.set(true);
        });

        MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> {
            var graphics = event.getGuiGraphics();
            ReplayServer replayServer = Flashback.getReplayServer();
            if (replayServer == null) {
                return;
            }
            if (Flashback.config.overlay.rtcOverlay) {
                long millis = replayServer.getInterpolatedRtc();
                if (millis != 0) {
                    ZonedDateTime dateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault());
                    String dateString = DateTimeFormatter.ofPattern("dd/MMM/uuuu HH:mm:ss.SSS zzz").format(dateTime);

                    Font font = Minecraft.getInstance().font;
                    int dateWidth = font.width(dateString);

                    final int outerPadding = 2;
                    final int innerPadding = 2;

                    int dateLeft = graphics.guiWidth() - outerPadding - innerPadding - dateWidth;
                    int fillLeft = dateLeft - innerPadding;

                    int textTop = outerPadding + innerPadding;

                    graphics.fill(fillLeft, outerPadding, graphics.guiWidth() - 2, 4 + font.lineHeight-1 + 2, 0x80000000);
                    graphics.drawString(font, dateString, dateLeft, textTop, -1, true);
                }
            }
        });

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase != TickEvent.Phase.START) return;
            Minecraft minecraft = Minecraft.getInstance();
            if (RECORDER != null && Flashback.config.advanced.synchronizeTicking && minecraft.hasSingleplayerServer()) {
                boolean isLevelLoaded = !(minecraft.screen instanceof LevelLoadingScreen);
                boolean willRecord = minecraft.level != null && minecraft.getOverlay() == null &&
                    !minecraft.isPaused() && !RECORDER.isPaused() && isLevelLoaded;
                while (willRecord && !synchronizeTickingCanTickClient.compareAndSet(true, false)) {
                    LockSupport.parkNanos("flashback synchronized ticking: waiting for server", 100000L);
                }
            }

            if (canReplaceScreen(minecraft.screen)) {
                openNewScreen(unsupportedLoader, minecraft.screen);
            }

            if (minecraft.level != null && delayedStartRecording > 0) {
                IntegratedServer integratedServer = minecraft.getSingleplayerServer();
                if (integratedServer != null && integratedServer.getClass() != IntegratedServer.class) {
                    delayedStartRecording = 0; // Only allow on actual integrated servers, not replay servers or any other custom server a mod might spin up
                } else if (Flashback.getConfig().recordingControls.automaticallyStart && RECORDER == null) {
                    delayedStartRecording -= 1;
                    if (delayedStartRecording == 0) {
                        startRecordingReplay();
                    }
                } else {
                    delayedStartRecording = 0;
                }
            }

            updateIsInReplay();

            if (RECORDER != null) {
                RECORDER.startTick();
            }

            if (createMarker1KeyBind.consumeClick()) {
                addMarker(Flashback.config.marker.markerOptions1);
            }
            if (createMarker2KeyBind.consumeClick()) {
                addMarker(Flashback.config.marker.markerOptions2);
            }
            if (createMarker3KeyBind.consumeClick()) {
                addMarker(Flashback.config.marker.markerOptions3);
            }
            if (createMarker4KeyBind.consumeClick()) {
                addMarker(Flashback.config.marker.markerOptions4);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END) return;
            synchronizeTickingCanTickClient.set(true);
        });

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase != TickEvent.Phase.START) return;
            if (RECORDER != null && Flashback.config.advanced.synchronizeTicking) {
                while (!synchronizeTickingCanTickServer.compareAndSet(true, false)) {
                    LockSupport.parkNanos("flashback synchronized ticking: waiting for client", 100000L);
                }
            }
        });

        if (ForgePlatform.getInstance().isModLoaded("distanthorizons")) {
            if (DhApi.getApiMajorVersion() >= 4) {
                Flashback.LOGGER.info("DistantHorizons detected. Enabling Flashback+DistantHorizons integration");
                supportsDistantHorizons = true;
                DistantHorizonsSupport.register();
            } else {
                Flashback.LOGGER.error("DistantHorizons is installed, but API version is too low ({}). Disabling integration.", DhApi.getApiMajorVersion());
            }
        }

        if (ForgePlatform.getInstance().isModLoaded("bobby")) {
            isBobbyLoaded = true;
        }
    }

    private static void openNewScreen(AtomicReference<String> unsupportedLoader, Screen currentScreen) {
        if (unsupportedLoader.get() != null) {
            String loaderName = unsupportedLoader.get();
            unsupportedLoader.set(null);
            if (System.currentTimeMillis() > Flashback.getConfig().internal.nextUnsupportedModLoaderWarning) {
                Component warning = Component.translatable("flashback.unsupported_loader.message", Component.literal(loaderName));

                Minecraft.getInstance().setScreen(new UnsupportedLoaderScreen(currentScreen,
                        Component.translatable("flashback.screen_unsupported"), warning));
                return;
            }
        }

        if (!pendingReplayRecovery.isEmpty()) {
            Component nl = FlashbackTextComponents.NEWLINE;
            Component title = Component.translatable("flashback.screen_recovery");
            Component description = Component.empty()
                    .append(Component.translatable("flashback.recovery1", Component.translatable("flashback.recovery2").withStyle(ChatFormatting.YELLOW))).append(nl)
                    .append(Component.translatable("flashback.recovery3")).append(nl).append(nl)
                    .append(Component.translatable("flashback.recovery4").withStyle(ChatFormatting.RED)).append(nl).append(nl)
                    .append(Component.translatable("flashback.recovery5").withStyle(ChatFormatting.GREEN));
            Minecraft.getInstance().setScreen(new RecoverRecordingsScreen(currentScreen, title, description, recover -> {
                switch (recover) {
                    case RECOVER -> {
                        pendingReplaySave.addAll(pendingReplayRecovery);
                        pendingReplayRecovery.clear();
                    }
                    case SKIP -> {
                        pendingReplayRecovery.clear();
                    }
                    case DELETE -> {
                        TempFolderProvider.tryDeleteStaleFolders(TempFolderProvider.TempFolderType.RECORDING);
                        pendingReplayRecovery.clear();
                    }
                }
            }));
            return;
        }

        if (!pendingReplaySave.isEmpty()) {
            Path recordFolder = pendingReplaySave.get(0);

            LocalDateTime dateTime = LocalDateTime.now();
            dateTime = dateTime.withNano(0);
            Minecraft.getInstance().setScreen(new SaveReplayScreen(currentScreen, recordFolder, dateTime.toString()));
            return;
        }

        if (pendingUnsupportedModsForRecording != null) {
            String mods = StringUtils.join(pendingUnsupportedModsForRecording, ", ");
            Component title = Component.translatable("flashback.incompatible_with_recording");
            Component description = Component.translatable("flashback.incompatible_with_recording_description").append(Component.literal(mods).withStyle(ChatFormatting.RED));
            Minecraft.getInstance().setScreen(new AlertScreen(() -> Minecraft.getInstance().setScreen(currentScreen), title, description));
            pendingUnsupportedModsForRecording = null;
            return;
        }

        if (delayedOpenConfig) {
            openConfigScreen(currentScreen);
            delayedOpenConfig = false;
            return;
        }
    }

    public static Screen createConfigScreen(Screen oldScreen) {
        return Lattice.createConfigScreen(configElements, () -> {
            config.saveToDefaultFolder();
            Minecraft.getInstance().options.save();
        }, oldScreen);
    }

    public static void openConfigScreen(Screen oldScreen) {
        Minecraft.getInstance().setScreen(createConfigScreen(oldScreen));
    }

    public static List<String> getReplayIncompatibleMods() {
        List<String> incompatible = new ArrayList<>();
        return incompatible;
    }

    public static List<String> getRecordingIncompatibleMods() {
        List<String> incompatible = new ArrayList<>();
        if (ForgePlatform.getInstance().isModLoaded("farsight")) {
            incompatible.add("Farsight");
        }
        if (incompatible.isEmpty()) {
            return null;
        }
        return incompatible;
    }

    private static @Nullable String findUnsupportedLoaders() {
        if (ForgePlatform.getInstance().isModLoaded("feather")) {
            return "Feather Client";
        } else {
            return null;
        }
    }

    private static boolean canReplaceScreen(Screen screen) {
        return screen == null || screen instanceof PauseScreen || screen instanceof TitleScreen
            || screen instanceof RealmsMainScreen || screen instanceof JoinMultiplayerScreen;
    }

    private void addMarker(FlashbackConfigV1.SubcategoryMarker.SubcategoryMarkerOptions options) {
        int colour;
        if (options.color == MarkerColour.CUSTOM_RGB) {
            String custom = options.customRGB.replaceAll("[^0-9a-fA-F]", "");
            if (custom.isEmpty()) {
                colour = 0;
            } else {
                colour = Integer.parseInt(custom, 16);
            }
        } else {
            colour = options.color.colour;
        }

        addMarker(colour, options.savePosition, options.description);
    }

    private void addMarker(@Nullable Integer colour, @Nullable Boolean savePosition, @Nullable String description) {
        Minecraft minecraft = Minecraft.getInstance();

        if (RECORDER == null) {
            minecraft.gui.getChat().addMessage(Component.translatable("flashback.mark_command.not_recording").withStyle(ChatFormatting.RED));
            return;
        }

        ReplayMarker.MarkerPosition position = null;
        if (savePosition == null || savePosition) {
            Entity camera = Minecraft.getInstance().getCameraEntity();
            if (camera != null) {
                position = new ReplayMarker.MarkerPosition(camera.getEyePosition().toVector3f(),
                    camera.level().dimension().toString());
            }
        }

        if (description != null && description.isBlank()) {
            description = null;
        }

        String feedback;
        if (description != null) {
            feedback = I18n.get("flashback.mark.added_with_description", description);
        } else if (colour != null) {
            feedback = I18n.get("flashback.mark.added_with_color", Integer.toHexString(colour));
        } else {
            feedback = I18n.get("flashback.mark.added");
        }

        if (position != null) {
            feedback += I18n.get("flashback.mark.added_at", position.position().x, position.position().y, position.position().z);
        }

        if (colour == null) {
            colour = 0xFF5555;
        }

        if (description != null) {
            description = description.trim();
            if (description.isEmpty()) {
                description = null;
            }
        }

        minecraft.gui.getChat().addMessage(Component.literal(feedback));
        RECORDER.addMarker(new ReplayMarker(colour, position, description));
    }

    private void deleteUnusedReplayStates() {
        Path flashbackDir = Flashback.getDataDirectory();
        Path replayDir = Flashback.getReplayFolder();
        Path replayStatesDir = flashbackDir.resolve("editor_states");

        if (!Files.exists(replayDir) || !Files.isDirectory(replayDir)) {
            return;
        }
        if (!Files.exists(replayStatesDir) || !Files.isDirectory(replayStatesDir)) {
            return;
        }

        List<String> recentReplays = new ArrayList<>(Flashback.config.internal.recentReplays);

        CompletableFuture.runAsync(() -> {
            long currentTime = System.currentTimeMillis();
            Map<UUID, Path> replayStates = new HashMap<>();

            // Find existing replay states
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(replayStatesDir)) {
                for (Path path : directoryStream) {
                    String filename = path.getFileName().toString();

                    String withoutExtension = null;

                    if (filename.endsWith(".json")) {
                        withoutExtension = filename.substring(0, filename.length() - 5);
                    } else if (filename.endsWith(".json.old")) {
                        withoutExtension = filename.substring(0, filename.length() - 9);
                    }

                    try {
                        boolean used = false;

                        JsonObject jsonObject = FlashbackGson.COMPRESSED.fromJson(Files.readString(path), JsonObject.class);
                        if (jsonObject.has("usedByPaths")) {
                            for (JsonElement usedBy : jsonObject.get("usedByPaths").getAsJsonArray()) {
                                String usedByStr = usedBy.getAsString();
                                if (recentReplays.contains(usedByStr)) {
                                    used = true;
                                    break;
                                }

                                Path usedByPath = Path.of(usedBy.getAsString());
                                if (Files.exists(usedByPath)) {
                                    used = true;
                                    break;
                                }
                            }
                        }

                        if (used) {
                            continue;
                        }
                    } catch (Exception ignored) {}

                    BasicFileAttributeView attributeView = Files.getFileAttributeView(path, BasicFileAttributeView.class);
                    BasicFileAttributes basicFileAttributes = attributeView.readAttributes();

                    long lastModified = Math.max(basicFileAttributes.creationTime().toMillis(), basicFileAttributes.lastModifiedTime().toMillis());
                    long timeDifference = Math.abs(currentTime - lastModified);
                    if (timeDifference < Duration.ofDays(30).toMillis()) {
                        continue;
                    }

                    if (withoutExtension != null) {
                        UUID uuid;
                        try {
                            uuid = UUID.fromString(withoutExtension);
                        } catch (Exception ignored) {
                            continue;
                        }

                        replayStates.put(uuid, path);
                    }
                }
            } catch (IOException ignored) {}

            if (replayStates.isEmpty()) {
                return;
            }

            // Find which uuids are still valid because they have replays
            Set<UUID> replayUuids = new HashSet<>();
            Set<Path> checkedReplayPaths = new HashSet<>();

            try {
                for (String recentReplayStr : recentReplays) {
                    Path path = Path.of(recentReplayStr);
                    if (!checkedReplayPaths.add(path)) {
                        continue;
                    }
                    if (!Files.exists(path)) {
                        continue;
                    }
                    readReplayUuidIntoSet(path, replayUuids);
                }
            } catch (IOException e) {
                Flashback.LOGGER.error("Unable read replay uuid", e);
                return;
            }

            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(replayDir)) {
                for (Path path : directoryStream) {
                    if (!checkedReplayPaths.add(path)) {
                        continue;
                    }
                    readReplayUuidIntoSet(path, replayUuids);
                }
            } catch (IOException e) {
                Flashback.LOGGER.error("Unable to iterate replay directory or read replay uuid", e);
                return;
            }

            for (Map.Entry<UUID, Path> entry : replayStates.entrySet()) {
                if (!replayUuids.contains(entry.getKey())) {
                    try {
                        Files.deleteIfExists(entry.getValue());
                    } catch (IOException ignored) {}
                }
            }
        }, Util.backgroundExecutor());
    }

    private static void readReplayUuidIntoSet(Path path, Set<UUID> replayUuids) throws IOException {
        if (!path.toString().endsWith(".zip")) {
            return;
        }

        String metadataString = null;

        try (FileSystem fs = FileSystems.newFileSystem(path)) {
            Path metadataPath = fs.getPath("/metadata.json");
            if (Files.exists(metadataPath)) {
                metadataString = Files.readString(metadataPath);
            }
        }

        if (metadataString != null) {
            JsonObject metadataJson = new Gson().fromJson(metadataString, JsonObject.class);
            FlashbackMeta metadata = FlashbackMeta.fromJson(metadataJson);
            if (metadata != null) {
                replayUuids.add(metadata.replayIdentifier);
            }
        }
    }

    public static FlashbackConfigV1 getConfig() {
        // On Forge, client setup runs during the initial resource reload, but screens (i.e. the pause
        // screen when the window loses focus) can already be opened before then
        if (config == null) {
            config = FlashbackConfigV1.tryLoadFromFolder(getConfigDirectory());
        }
        return config;
    }

    @Nullable
    public static ReplayServer getReplayServer() {
        if (Minecraft.getInstance().getSingleplayerServer() instanceof ReplayServer replayServer) {
            return replayServer;
        }
        return null;
    }

    public static void removePendingReplaySave(Path recordFolder) {
        pendingReplaySave.remove(recordFolder);
    }

    public static boolean isExporting() {
        return EXPORT_JOB != null && EXPORT_JOB.isRunning();
    }

    public static void updateIsInReplay() {
        isInReplay = Minecraft.getInstance().getSingleplayerServer() instanceof ReplayServer;
    }

    public static boolean isInReplay() {
        return isInReplay || isOpeningReplay;
    }

    public static long getVisualMillis() {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
            float tick;

            ExportJob exportJob = Flashback.EXPORT_JOB;
            if (exportJob != null) {
                tick = (float) exportJob.getCurrentTickDouble();
            } else {
                tick = (float) replayServer.getPartialReplayTick();
            }

            return (long)(tick * 50L);
        } else {
            return Util.getMillis();
        }
    }

    private int startRecordingReplay(CommandContext<net.minecraft.commands.CommandSourceStack> command) {
        startRecordingReplay();
        return 0;
    }

    private int finishRecordingReplay(CommandContext<net.minecraft.commands.CommandSourceStack> command) {
        finishRecordingReplay();
        return 0;
    }

    private int openFlashbackConfig(CommandContext<net.minecraft.commands.CommandSourceStack> command) {
        delayedOpenConfig = true;
        return 0;
    }

    public static void startRecordingReplay() {
        if (RECORDER != null) {
            SystemToast.add(Minecraft.getInstance().getToasts(), FlashbackSystemToasts.RECORDING_TOAST,
                    Component.translatable("flashback.toast.already_recording"), Component.translatable("flashback.toast.already_recording_description"));
            return;
        }

        List<String> unsupported = getRecordingIncompatibleMods();
        if (unsupported != null && !unsupported.isEmpty()) {
            pendingUnsupportedModsForRecording = unsupported;
            return;
        }

        RECORDER = new Recorder(Minecraft.getInstance().level.registryAccess());
        if (Flashback.getConfig().recordingControls.showRecordingToasts) {
            SystemToast.add(Minecraft.getInstance().getToasts(), FlashbackSystemToasts.RECORDING_TOAST,
                    FlashbackTextComponents.FLASHBACK, Component.translatable("flashback.toast.started_recording"));
        }
    }

    public static void pauseRecordingReplay(boolean pause) {
        if (RECORDER == null) return;
        RECORDER.setPaused(pause);

        if (Flashback.getConfig().recordingControls.showRecordingToasts) {
            SystemToast.add(Minecraft.getInstance().getToasts(), FlashbackSystemToasts.RECORDING_TOAST,
                    FlashbackTextComponents.FLASHBACK, Component.translatable(pause ? "flashback.toast.paused_recording" : "flashback.toast.unpaused_recording"));
        }
    }

    public static void cancelRecordingReplay() {
        Recorder recorder = RECORDER;
        RECORDER = null;

        Path recordFolder = recorder.finish();
        try {
            FileUtils.deleteDirectory(recordFolder.toFile());
        } catch (Exception e) {
            Flashback.LOGGER.error("Exception deleting record folder", e);
        }

        if (Flashback.getConfig().recordingControls.showRecordingToasts) {
            SystemToast.add(Minecraft.getInstance().getToasts(), FlashbackSystemToasts.RECORDING_TOAST,
                FlashbackTextComponents.FLASHBACK, Component.translatable("flashback.toast.cancelled_recording"));
        }
    }

    public static void finishRecordingReplay() {
        if (RECORDER == null) {
            SystemToast.add(Minecraft.getInstance().getToasts(), FlashbackSystemToasts.RECORDING_TOAST,
                    Component.translatable("flashback.toast.not_recording"), Component.translatable("flashback.toast.cant_finish_when_not_recording"));
            return;
        }

        Recorder recorder = RECORDER;
        RECORDER = null;
        recorder.endTickWithContext(true);

        if (Flashback.getConfig().recordingControls.quicksave) {
            Path replayDir = getReplayFolder();

            if (!Files.exists(replayDir)) {
                try {
                    Files.createDirectories(replayDir);
                } catch (IOException ignored) {}
            }

            String filename;
            try {
                LocalDateTime dateTime = LocalDateTime.now();
                dateTime = dateTime.withNano(0);
                filename = FileUtil.findAvailableName(replayDir, dateTime.toString(), ".zip");
            } catch (IOException e) {
                Flashback.LOGGER.error("Error while trying to determine filename", e);
                filename = UUID.randomUUID() + ".zip";
            }

            Path outputFile = replayDir.resolve(filename);
            ReplayExporter.export(recorder.finish(), outputFile, null);
        } else {
            pendingReplaySave.add(recorder.finish());
        }

        if (Flashback.getConfig().recordingControls.showRecordingToasts) {
            SystemToast.add(Minecraft.getInstance().getToasts(), FlashbackSystemToasts.RECORDING_TOAST,
                FlashbackTextComponents.FLASHBACK, Component.translatable("flashback.toast.finished_recording"));
        }
    }

    @Nullable
    public static AbstractClientPlayer getSpectatingPlayer() {
        if (!isInReplay()) {
            return null;
        }
        if (Minecraft.getInstance().getCameraEntity() instanceof AbstractClientPlayer clientPlayer) {
            if (clientPlayer != Minecraft.getInstance().player) {
                return clientPlayer;
            }
        }
        return null;
    }

    public static void openReplayFromFileBrowser() {
        String defaultFolder = Flashback.getReplayFolder().toString();
        AsyncFileDialogs.openFileDialog(defaultFolder, "Zip File", "zip").thenAccept(pathStr -> {
            if (pathStr != null) {
                Path path = Path.of(pathStr);
                Minecraft.getInstance().submit(() -> {
                    Flashback.openReplayWorld(path);
                });
            }
        });
    }

    public static GameRules createReplayGameRules(FeatureFlagSet featureFlagSet) {
        GameRules gameRules = new GameRules();
        gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        gameRules.getRule(GameRules.RULE_DOENTITYDROPS).set(false, null);
        gameRules.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, null);
        gameRules.getRule(GameRules.RULE_DISABLE_RAIDS).set(true, null);
        gameRules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, null);
        gameRules.getRule(GameRules.RULE_DO_WARDEN_SPAWNING).set(false, null);
        gameRules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, null);
        gameRules.getRule(GameRules.RULE_DO_VINES_SPREAD).set(false, null);
        gameRules.getRule(GameRules.RULE_DOFIRETICK).set(false, null);
        gameRules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        gameRules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
        return gameRules;
    }

    public static void openReplayWorld(Path path) {
        // Disconnect
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            minecraft.level.disconnect();
        }
        minecraft.clearLevel();
        minecraft.setScreen(new TitleScreen());

        ReplayUI.shownRegistryErrorWarning = false;
        ReplayUI.shownPlayerSpawnErrorWarning = false;

        // Add as recent
        String pathStr = path.toString();
        FlashbackConfigV1 config = Flashback.getConfig();
        config.internal.recentReplays.remove(pathStr);
        config.internal.recentReplays.add(0, pathStr);
        if (config.internal.recentReplays.size() > 32) {
            config.internal.recentReplays.remove(config.internal.recentReplays.size() - 1);
        }
        config.delayedSaveToDefaultFolder();

        // Actually load
        FileSystem playbackFileSystem = null;
        try {
            FlashbackMeta metadata;
            try {
                Flashback.LOGGER.info("Loading replay from {}", path);

                playbackFileSystem = FileSystems.newFileSystem(path);

                // Try load metadata
                Path metadataPath = playbackFileSystem.getPath("/metadata.json");
                String metadataJson = Files.readString(metadataPath);
                metadata = FlashbackMeta.fromJson(FlashbackGson.PRETTY.fromJson(metadataJson, JsonObject.class));
                if (metadata == null) {
                    Flashback.LOGGER.error("Unable to read metadata from {}", path);
                    return;
                }

                // File-picker opens must enforce the same protocol check as the replay list.
                if (metadata.protocolVersion != 0 && metadata.protocolVersion != net.minecraft.SharedConstants.getProtocolVersion()) {
                    minecraft.setScreen(new net.minecraft.client.gui.screens.AlertScreen(
                        () -> minecraft.setScreen(new TitleScreen()), Component.literal("Flashback"),
                        Component.translatable("flashback.incompatible_replay_version_protocol",
                            Component.literal(Integer.toString(metadata.protocolVersion)),
                            Component.literal(Integer.toString(net.minecraft.SharedConstants.getProtocolVersion())))));
                    return;
                }

                // Log any changes to mod list
                if (metadata.modVersions != null) {
                    ModListHelper.calculateChanges(metadata.modVersions).log();
                }

                // Mark that editor state was used by this path so it doesn't get automatically cleaned up
                var editorState = EditorStateManager.get(metadata.replayIdentifier);
                editorState.usedByPaths.add(path.toString());
            } catch (Exception e) {
                Flashback.LOGGER.error("Unable to read replay file", e);
                return;
            }

            isOpeningReplay = true;

            UUID replayUuid = UUID.randomUUID();
            Path replayTemp = TempFolderProvider.createTemp(TempFolderProvider.TempFolderType.SERVER, replayUuid);
            FileUtils.deleteDirectory(replayTemp.toFile());

            LevelStorageSource source = LevelStorageSource.createDefault(replayTemp.resolve("saves"));
            LevelStorageSource.LevelStorageAccess access = source.createAccess("replay");
            PackRepository packRepository = ServerPacksSource.createPackRepository(access);

            packRepository.reload();

            GameRules gameRules = createReplayGameRules(FeatureFlags.DEFAULT_FLAGS);

            WorldDataConfiguration worldDataConfiguration = new WorldDataConfiguration(new DataPackConfig(List.of(), List.of()), FeatureFlags.DEFAULT_FLAGS);
            LevelSettings levelSettings = new LevelSettings("Replay", GameType.SPECTATOR,
                false, Difficulty.NORMAL, true, gameRules, worldDataConfiguration);
            WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(packRepository, worldDataConfiguration, false, true);
            WorldLoader.InitConfig initConfig = new WorldLoader.InitConfig(packConfig, Commands.CommandSelection.DEDICATED, 4);

            WorldStem worldStem = Util.blockUntilDone(executor -> WorldLoader.load(initConfig, dataLoadContext -> {
                Registry<LevelStem> registry = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable()).freeze();

                Holder.Reference<Biome> plains = dataLoadContext.datapackWorldgen().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
                Holder.Reference<DimensionType> overworld = dataLoadContext.datapackWorldgen().registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD);

                MappedRegistry<LevelStem> dimensions = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable());
                dimensions.register(LevelStem.OVERWORLD, new LevelStem(overworld, new EmptyLevelSource(plains)), Lifecycle.stable());
                WorldDimensions worldDimensions = new WorldDimensions(dimensions.freeze());
                WorldDimensions.Complete complete = worldDimensions.bake(registry);

                return new WorldLoader.DataLoadOutput<>(new PrimaryLevelData(levelSettings,
                    new WorldOptions(0L, false, false), complete.specialWorldProperty(), complete.lifecycle()),
                    complete.dimensionsRegistryAccess());
            }, WorldStem::new, Util.backgroundExecutor(), executor)).get();

            ((MinecraftExt)Minecraft.getInstance()).flashback$startReplayServer(access, packRepository, worldStem, Optional.of(gameRules), new MinecraftExt.StartReplayServerInfo(replayUuid, playbackFileSystem, metadata));
            playbackFileSystem = null;

            TaskbarManager.launchTaskbarManager();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (playbackFileSystem != null) {
                try {
                    playbackFileSystem.close();
                } catch (Exception ignored) {}
            }

            isOpeningReplay = false;
        }
    }
}
