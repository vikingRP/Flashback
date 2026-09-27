package com.moulberry.flashback.mixin.compat.bobby;

import com.bawnorton.mixinsquared.TargetHandler;
import com.moulberry.flashback.Flashback;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Coerce;

@IfModLoaded("bobby")
@Pseudo
@Mixin(value = IntegratedServer.class, priority = 1500)
public class MixinIntegratedServer {
    @TargetHandler(
            mixin = "de.johni0702.minecraft.bobby.mixin.IntegratedServerMixin",
            name = "bobbyViewDistanceOverwrite"
    )
    @Redirect(
            method = "@MixinSquared:Handler",
            at = @At(
                    value = "INVOKE",
                    target = "Lde/johni0702/minecraft/bobby/BobbyConfig;getViewDistanceOverwrite()I",
                    remap = false
            )
    )
    public int flashback$overrideViewDistanceOverwrite(@Coerce Object instance) {
        if (Flashback.isInReplay()) {
            return 0;//we dont want server distance override
        }
        try {
            return (Integer) instance.getClass().getMethod("getViewDistanceOverwrite").invoke(instance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Bobby configuration is incompatible with Flashback", e);
        }
    }
}
