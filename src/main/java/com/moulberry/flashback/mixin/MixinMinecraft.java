package com.moulberry.flashback.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.utils.FramebufferUtils;
import com.moulberry.flashback.FreezeSlowdownFormula;
import com.moulberry.flashback.utils.WindowSizeTracker;
import com.moulberry.flashback.combo_options.GlowingOverride;
import com.moulberry.flashback.configuration.FlashbackConfigV1;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportJobQueue;
import com.moulberry.flashback.ext.WindowExt;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.keyframe.handler.TickrateKeyframeCapture;
import com.moulberry.flashback.sound.FlashbackAudioManager;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.exporting.PerfectFrames;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.visuals.AccurateEntityPositionHandler;
import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.minecraft.client.gui.Gui;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.Util;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import com.moulberry.flashback.playback.ReplayTickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft extends ReentrantBlockableEventLoop<Runnable> implements MinecraftExt {

    public MixinMinecraft(String string) {
        super(string);
    }

    @Shadow
    @Nullable
    public ClientLevel level;

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Shadow
    @Final
    public Timer timer;

    @Unique
    public long clientTickCount;

    @Shadow @Final public Options options;

    @Shadow @Final public LevelRenderer levelRenderer;



    @Shadow @Nullable public abstract Entity getCameraEntity();

    @Shadow
    public abstract void doWorldLoad(String name, LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem worldStem, boolean bl);

    @Shadow
    @Final
    private Window window;

    @Shadow
    @Final
    public Gui gui;

    @Unique
    private RenderTarget compositeRenderTarget = null;

    @WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(II)V"))
    public void flashback$compositeFrame(RenderTarget source, int width, int height, Operation<Void> original) {
        ReplayUI.drawOverlay();
        ((WindowExt)(Object)this.window).flashback$updateScaledFramebuffer(true);
        if (ReplayUI.isActive() && ReplayUI.compositeOnTop != null) {
            int framebufferWidth = WindowSizeTracker.getWidth(window);
            int framebufferHeight = WindowSizeTracker.getHeight(window);
            this.compositeRenderTarget = FramebufferUtils.resizeOrCreateFramebuffer(this.compositeRenderTarget, framebufferWidth, framebufferHeight, false);
            FramebufferUtils.clear(this.compositeRenderTarget, FramebufferUtils.TRANSPARENT_CLEAR_COLOUR);
            if (ReplayUI.frameWidth > 1 && ReplayUI.frameHeight > 1) {
                float top = (float) ReplayUI.frameY / ReplayUI.viewportSizeY;
                float left = (float) ReplayUI.frameX / ReplayUI.viewportSizeX;
                float w = (float) ReplayUI.frameWidth / ReplayUI.viewportSizeX;
                float h = (float) ReplayUI.frameHeight / ReplayUI.viewportSizeY;
                FramebufferUtils.blitTo(source.getColorTextureId(), this.compositeRenderTarget, left, top, left+w, top+h);
            }
            FramebufferUtils.blitTo(ReplayUI.compositeOnTop.getColorTextureId(), this.compositeRenderTarget, 0, 0, 1, 1);
            original.call(this.compositeRenderTarget, framebufferWidth, framebufferHeight);
        } else {
            original.call(source, width, height);
        }
    }

    @Inject(method = "resizeDisplay", at = @At("HEAD"))
    public void framebufferSizeChanged(CallbackInfo ci) {
        ((WindowExt)(Object)this.window).flashback$updateScaledFramebuffer(false);
    }

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    public void pauseGame(boolean bl, CallbackInfo ci) {
        if (Flashback.EXPORT_JOB != null) {
            ci.cancel();
        }
    }

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void shouldEntityAppearGlowing(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            GlowingOverride glowingOverride = editorState.glowingOverride.get(entity.getUUID());

            if (glowingOverride == GlowingOverride.FORCE_GLOW) {
                cir.setReturnValue(true);
            } else if (glowingOverride == GlowingOverride.FORCE_NO_GLOW) {
                cir.setReturnValue(false);
            }
        }
    }

    @WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;updateSource(Lnet/minecraft/client/Camera;)V"))
    public void runTick_updateSource(SoundManager instance, Camera camera, Operation<Void> original) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            Camera audioCamera = editorState.getAudioCamera();
            if (audioCamera != null) {
                original.call(instance, audioCamera);
                return;
            }
        }

        original.call(instance, camera);
    }

    @Unique
    private boolean inReplayLast = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void flashback$advanceReplayClock(CallbackInfo ci) {
        ReplayTickRateManager.client().tick();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    public void tick(CallbackInfo ci) {
        this.clientTickCount++;
        if (Flashback.RECORDER != null) {
            Flashback.RECORDER.endTickWithContext(false);
        }

        EditorStateManager.saveIfNeeded();

        ReplayServer replayServer = Flashback.getReplayServer();

        boolean inReplay = replayServer != null;
        if (inReplay != inReplayLast) {
            inReplayLast = inReplay;
            if (inReplay) {
                this.options.hideGui = false;
            } else {
                EditorStateManager.reset();
            }
        }

        FlashbackConfigV1 config = Flashback.getConfig();
        if (inReplay && !config.advanced.disableThirdPersonCancel) {
            // Force camera type to first person
            if (ReplayUI.isActive() && this.player != null && this.getCameraEntity() == this.player && this.options.getCameraType() != CameraType.FIRST_PERSON) {
                this.options.setCameraType(CameraType.FIRST_PERSON);
                ReplayUI.setInfoOverlay("Forced perspective to First-Person");
            }
        }
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;tick()V"))
    private boolean flashback$particleClock(net.minecraft.client.particle.ParticleEngine particles) {
        return !Flashback.isInReplay() || ReplayTickRateManager.client().runsNormally();
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;tick()V"))
    private boolean flashback$rendererClock(LevelRenderer renderer) {
        return !Flashback.isInReplay() || ReplayTickRateManager.client().runsNormally();
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;animateTick(III)V"))
    private boolean flashback$ambientClock(ClientLevel level, int x, int y, int z) {
        return !Flashback.isInReplay() || ReplayTickRateManager.client().runsNormally();
    }

    @Unique
    private int serverTickFreezeDelayStart = -1;
    @Unique
    private double clientTickFreezeDelayStart = -1;

    @Unique private float flashback$runningPartialTick;
    @Unique private float flashback$frozenPartialTick;
    @Unique private boolean flashback$partialTickFrozen;

    @WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Timer;advanceTime(J)I"))
    private int flashback$advanceTimer(Timer timer, long millis, Operation<Integer> original) {
        if (flashback$partialTickFrozen) timer.partialTick = flashback$runningPartialTick;
        timer.msPerTick = getTickTargetMillis(50.0f);
        int ticks = original.call(timer, millis);
        flashback$runningPartialTick = timer.partialTick;
        var clock = ReplayTickRateManager.client();
        flashback$partialTickFrozen = Flashback.isInReplay() && clock.isFrozen() && !clock.runsNormally();
        if (flashback$partialTickFrozen) timer.partialTick = flashback$frozenPartialTick;
        else flashback$frozenPartialTick = timer.partialTick;
        return ticks;
    }

    @Unique
    private float getTickTargetMillis(float f) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
            if (this.level == null) {
                clientTickFreezeDelayStart = -1;
                serverTickFreezeDelayStart = -1;
                return f;
            }

            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState != null && !replayServer.replayPaused) {
                double partialReplayTick = replayServer.getPartialReplayTick();

                TickrateKeyframeCapture capture = new TickrateKeyframeCapture();
                editorState.applyKeyframes(capture, (float) partialReplayTick);

                if (capture.frozen && capture.frozenDelay > 0) {
                    if (clientTickFreezeDelayStart < 0) {
                        clientTickFreezeDelayStart = this.clientTickCount + 1;
                        serverTickFreezeDelayStart = (int) partialReplayTick;

                        ReplayTickRateManager tickRateManager = ReplayTickRateManager.client();
                        tickRateManager.setFrozenTicksToRun(capture.frozenDelay <= 5 ? 1 : 2);
                    }

                    double freezeClientTicks = capture.frozenDelay <= 5 ? 0.999 : 1.999;
                    double freezeDerivative = capture.frozenDelay <= 5 ? 1.0 : 0.5;

                    double deltaFromStart = partialReplayTick - serverTickFreezeDelayStart;

                    if (deltaFromStart >= 0 && deltaFromStart <= capture.frozenDelay) {
                        double freezePowerBase = FreezeSlowdownFormula.getFreezePowerBase(capture.frozenDelay, freezeDerivative);
                        double clientTicks = freezeClientTicks * FreezeSlowdownFormula.calculateFreezeClientTick(deltaFromStart,
                            capture.frozenDelay, freezePowerBase);

                        double currentClientTicks = this.clientTickCount + this.timer.partialTick - clientTickFreezeDelayStart;
                        double freezeRate = Math.max(0.01f, Math.min(1f, clientTicks - currentClientTicks));

                        float tickrate = Math.max(1f, capture.tickrate) * (float) freezeRate;
                        return 1000f / tickrate;
                    }
                } else {
                    clientTickFreezeDelayStart = -1;
                    serverTickFreezeDelayStart = -1;
                }

                ReplayTickRateManager tickRateManager = ReplayTickRateManager.client();
                if (tickRateManager.runsNormally()) {
                    float manualMultiplier = replayServer.getDesiredTickRate(true) / 20.0f;
                    return 1000f / Math.max(1f, capture.tickrate * manualMultiplier);
                }
            } else {
                ReplayTickRateManager tickRateManager = ReplayTickRateManager.client();
                if (tickRateManager.runsNormally()) {
                    return tickRateManager.millisecondsPerTick();
                }
            }

        }
        return f;
    }

    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
    public void disconnectHead(Screen screen, CallbackInfo ci) {
        try {
            if (Flashback.getConfig().recordingControls.automaticallyFinish && Flashback.RECORDER != null) {
                Flashback.finishRecordingReplay();
            }
        } catch (Exception e) {
            Flashback.LOGGER.error("Failed to finish replay on disconnect", e);
        }
    }

    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("RETURN"))
    public void disconnectReturn(Screen screen, CallbackInfo ci) {
        Flashback.updateIsInReplay();
        ReplayTickRateManager clock = ReplayTickRateManager.client();
        clock.setTickRate(20);
        clock.setFrozen(false);
        clock.setFrozenTicksToRun(0);
        clock.tick();
        this.timer.msPerTick = 50;
        this.flashback$partialTickFrozen = false;
        this.clientTickFreezeDelayStart = -1;
        this.serverTickFreezeDelayStart = -1;
    }

    @Unique
    private final Timer localPlayerTimer = new Timer(20.0f, 0);

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V", shift = At.Shift.AFTER))
    public void runTick_runAllTasks(boolean runTick, CallbackInfo ci) {
        if (ExportJobQueue.drainingQueue) {
            if (ExportJobQueue.queuedJobs.isEmpty()) {
                ExportJobQueue.drainingQueue = false;
            } else if (Flashback.EXPORT_JOB == null) {
                Flashback.EXPORT_JOB = new ExportJob(ExportJobQueue.queuedJobs.remove(0));
            }
        }

        if (Flashback.EXPORT_JOB != null && !ReplayUI.isActive()) {
            try {
                PerfectFrames.enable();
                Flashback.EXPORT_JOB.run();
            } finally {
                PerfectFrames.disable();
                Flashback.EXPORT_JOB = null;
            }
        }

        if (Flashback.isInReplay()) {
            long millis = Util.getMillis();
            int localPlayerTicks = this.localPlayerTimer.advanceTime(millis);
            if (this.flashback$overridingLocalPlayerTimer()) {
                localPlayerTicks = Math.min(10, localPlayerTicks);
                this.flashback$tickingLocalPlayer = true;
                try {
                    for (int i = 0; i < localPlayerTicks; i++) {
                        this.level.guardEntityTick(this.level::tickNonPassenger, this.player);
                    }
                } finally {
                    this.flashback$tickingLocalPlayer = false;
                }
            }
        }
    }

    @Unique private boolean flashback$tickingLocalPlayer;

    @Override
    public boolean flashback$isTickingLocalPlayer() { return this.flashback$tickingLocalPlayer; }

    @Override
    public boolean flashback$overridingLocalPlayerTimer() {
        return !Flashback.isExporting() && this.level != null && this.player != null && !this.player.isPassenger() && !this.player.isRemoved() && (ReplayTickRateManager.client().isFrozen() || Math.round(this.getTickTargetMillis(50)) != 50);
    }

    @Override
    public float flashback$getLocalPlayerPartialTick(float originalPartialTick) {
        if (this.getCameraEntity() != this.player || !this.flashback$overridingLocalPlayerTimer()) {
            return originalPartialTick;
        }
        return this.localPlayerTimer.partialTick;
    }

    @Unique
    private final AtomicBoolean applyKeyframes = new AtomicBoolean(false);

    @Override
    public void flashback$applyKeyframes() {
        this.applyKeyframes.set(true);
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/platform/Window;setErrorSection(Ljava/lang/String;)V",
        ordinal = 1), cancellable = true)
    public void runTick_setErrorSection(boolean bl, CallbackInfo ci) {
        if (Flashback.RECORDER != null && this.player != null) {
            Flashback.RECORDER.trackPartialPosition(this.player, this.timer.partialTick);
        }
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer == null) {
            FlashbackAudioManager.stopAll();
            return;
        }

        LocalPlayer player = this.player;
        Timer timer = this.timer;

        AccurateEntityPositionHandler.apply(this.level, timer.partialTick);

        boolean paused = replayServer.replayPaused;
        boolean forceApplyKeyframes = this.applyKeyframes.compareAndSet(true, false);
        if (paused) {
            FlashbackAudioManager.pauseAll();
        }
        if (!paused || forceApplyKeyframes) {
            if (!paused) {
                FlashbackAudioManager.startHandling();
            }

            try {
                EditorState editorState = EditorStateManager.get(replayServer.getMetadata().replayIdentifier);
                editorState.applyKeyframes(new MinecraftKeyframeHandler((Minecraft) (Object) this), (float) replayServer.getPartialReplayTick());
            } finally {
                if (!paused) {
                    FlashbackAudioManager.finishHandling();
                }
            }
        }
        if (!replayServer.doClientRendering()) {
            ci.cancel();
        }
    }

    @Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
    public void pauseIfInactive(boolean pauseOnly, CallbackInfo ci) {
        // Only suppress the automatic pause on focus loss; Escape must still open the pause menu
        if (Flashback.isInReplay() && !((Minecraft) (Object) this).isWindowActive()) {
            ci.cancel();
        }
    }

    @Unique
    private final ThreadLocal<StartReplayServerInfo> info = new ThreadLocal<>();

    // The constructor is in Minecraft's synthetic server factory; match it by descriptor.
    @WrapOperation(method = "*", at = @At(value = "NEW", target = "(Ljava/lang/Thread;Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/server/packs/repository/PackRepository;Lnet/minecraft/server/WorldStem;Lnet/minecraft/server/Services;Lnet/minecraft/server/level/progress/ChunkProgressListenerFactory;)Lnet/minecraft/client/server/IntegratedServer;"))
    private IntegratedServer flashback$createReplayServer(Thread thread, Minecraft client,
            LevelStorageSource.LevelStorageAccess access, PackRepository packs, WorldStem stem,
            Services services, ChunkProgressListenerFactory progress, Operation<IntegratedServer> original) {
        StartReplayServerInfo info = this.info.get();
        if (info != null) {
            return new ReplayServer(thread, client, access, packs, stem, services, progress, info);
        }
        return original.call(thread, client, access, packs, stem, services, progress);
    }

    @Inject(method = "doWorldLoad", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;singleplayerServer:Lnet/minecraft/client/server/IntegratedServer;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    public void afterSetSingleplayerServer(String name, LevelStorageSource.LevelStorageAccess levelSourceAccess, PackRepository packRepository, WorldStem worldStem, boolean newWorld, CallbackInfo ci) {
        Flashback.updateIsInReplay();
    }

    @Override
    public void flashback$startReplayServer(LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem stem, Optional<GameRules> gameRules, StartReplayServerInfo info) {
        this.info.set(info);
        try {
            this.doWorldLoad("replay", levelStorageAccess, packRepository, stem, false);
        } finally {
            this.info.remove();
        }
    }

}
