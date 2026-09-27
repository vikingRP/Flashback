package com.moulberry.flashback.mixin.playback.lenient_registry;
import com.moulberry.flashback.Flashback;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;
/** Resolve absent particle/defaulted entries while reading a replay's dynamic registries. */
@Mixin(MappedRegistry.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class MixinRegistry {
    @Inject(method="getHolder(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;", at=@At("RETURN"), cancellable=true)
    private void replayFallback(ResourceKey key, CallbackInfoReturnable<Optional<Holder.Reference<?>>> cir) {
        if (!Flashback.isInReplay() || cir.getReturnValue().isPresent()) return;
        Registry registry = (Registry)(Object)this;
        if (registry == BuiltInRegistries.PARTICLE_TYPE) cir.setReturnValue(registry.getHolder(0));
        else if (registry instanceof DefaultedRegistry defaults)
            cir.setReturnValue(registry.getHolder(registry.getId(defaults.get(defaults.getDefaultKey()))));
    }
}
