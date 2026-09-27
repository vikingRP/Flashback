package com.moulberry.flashback.mixin.playback;
import com.moulberry.flashback.ext.ReplayInterpolatedEntity;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
@Mixin(Boat.class)
public abstract class MixinBoatInterpolation implements ReplayInterpolatedEntity {
    @Shadow private int lerpSteps;
    @Shadow private double lerpX;
    @Shadow private double lerpY;
    @Shadow private double lerpZ;
    @Shadow private double lerpYRot;
    @Shadow private double lerpXRot;
    @Override public void flashback$finishInterpolation() {
        if (this.lerpSteps > 0) {
            ((Boat)(Object)this).moveTo(this.lerpX, this.lerpY, this.lerpZ, (float)this.lerpYRot, (float)this.lerpXRot);
            this.lerpSteps = 0;
        }
    }
}
