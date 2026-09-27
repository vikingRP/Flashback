package com.moulberry.flashback.mixin.playback;
import com.moulberry.flashback.ext.ReplayInterpolatedEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
@Mixin(AbstractMinecart.class)
public abstract class MixinAbstractMinecartInterpolation implements ReplayInterpolatedEntity {
    @Shadow private int lSteps;
    @Shadow private double lx;
    @Shadow private double ly;
    @Shadow private double lz;
    @Shadow private double lyr;
    @Shadow private double lxr;
    @Override public void flashback$finishInterpolation() {
        if (this.lSteps > 0) {
            ((AbstractMinecart)(Object)this).moveTo(this.lx, this.ly, this.lz, (float)this.lyr, (float)this.lxr);
            this.lSteps = 0;
        }
    }
}
