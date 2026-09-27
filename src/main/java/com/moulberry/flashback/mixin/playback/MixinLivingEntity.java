package com.moulberry.flashback.mixin.playback;

import com.moulberry.flashback.Flashback;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class MixinLivingEntity implements com.moulberry.flashback.ext.ReplayInterpolatedEntity {
    @org.spongepowered.asm.mixin.Shadow protected int lerpSteps;
    @org.spongepowered.asm.mixin.Shadow protected double lerpX;
    @org.spongepowered.asm.mixin.Shadow protected double lerpY;
    @org.spongepowered.asm.mixin.Shadow protected double lerpZ;
    @org.spongepowered.asm.mixin.Shadow protected double lerpYRot;
    @org.spongepowered.asm.mixin.Shadow protected double lerpXRot;
    @org.spongepowered.asm.mixin.Shadow protected int lerpHeadSteps;
    @org.spongepowered.asm.mixin.Shadow protected double lyHeadRot;

    @Override public void flashback$finishInterpolation() {
        LivingEntity entity = (LivingEntity)(Object)this;
        if (this.lerpSteps > 0) {
            entity.moveTo(this.lerpX, this.lerpY, this.lerpZ, (float)this.lerpYRot, (float)this.lerpXRot);
            this.lerpSteps = 0;
        }
        if (this.lerpHeadSteps > 0) {
            entity.setYHeadRot((float)this.lyHeadRot);
            this.lerpHeadSteps = 0;
        }
    }


    // Prevent invisible/glowing state from being updated based on potion effects inside a replay


    @Inject(method = "updateInvisibilityStatus", at = @At("HEAD"), cancellable = true)
    public void updateInvisibilityStatus(CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            ci.cancel();
        }
    }

    @Inject(method = "updateGlowingStatus", at = @At("HEAD"), cancellable = true)
    public void updateGlowingStatus(CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            ci.cancel();
        }
    }

}
