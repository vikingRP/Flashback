package com.moulberry.flashback.mixin.playback;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.visuals.AccurateEntityPositionHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Shadow private float eyeHeightOld;
    @Shadow public float eyeHeight;
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPosition(double x, double y, double z);

    @ModifyVariable(method = "setup", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float flashback$cameraPartialTick(float tick) {
        Minecraft client = Minecraft.getInstance();
        if (Flashback.isInReplay() && client.getCameraEntity() == client.player) {
            return ((MinecraftExt) client).flashback$getLocalPlayerPartialTick(tick);
        }
        return tick;
    }

    @Inject(method = "setup", at = @At("RETURN"))
    private void flashback$accuratePosition(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        if (entity == null || detached) return;
        var rotation = AccurateEntityPositionHandler.getAccurateRotation(entity, partialTick);
        if (rotation != null) this.setRotation(rotation.y, rotation.x);
        var position = AccurateEntityPositionHandler.getAccuratePosition(entity, partialTick);
        if (position != null) this.setPosition(position.x, position.y + Mth.lerp(partialTick, this.eyeHeightOld, this.eyeHeight), position.z);
    }
}
