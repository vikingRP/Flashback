package com.moulberry.flashback.playback;
import com.moulberry.flashback.ext.ReplayInterpolatedEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
public final class ReplayInterpolation {
    private ReplayInterpolation() {}
    public static void finish(Entity entity) {
        if (entity instanceof ReplayInterpolatedEntity interpolated) interpolated.flashback$finishInterpolation();
        entity.setOldPosAndRot();
        if (entity instanceof LivingEntity living) {
            living.yHeadRotO = living.yHeadRot;
            living.yBodyRotO = living.yBodyRot;
        }
    }
}
