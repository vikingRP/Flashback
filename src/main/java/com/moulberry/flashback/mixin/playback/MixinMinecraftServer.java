package com.moulberry.flashback.mixin.playback;
import com.moulberry.flashback.Flashback;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.ReloadableServerResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Preserve the tags installed from recorded Login/PLAY packets during resource reloads. */
@Mixin(ReloadableServerResources.class)
public abstract class MixinMinecraftServer {
    @Inject(method="updateRegistryTags", at=@At("HEAD"), cancellable=true)
    private void keepRecordedTags(RegistryAccess registries, CallbackInfo ci) {
        if (Flashback.isInReplay()) ci.cancel();
    }
}
