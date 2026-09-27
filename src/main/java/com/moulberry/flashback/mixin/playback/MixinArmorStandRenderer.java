package com.moulberry.flashback.mixin.playback;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ArmorStandRenderer.class)
public abstract class MixinArmorStandRenderer {
    @WrapOperation(method="setupRotations(Lnet/minecraft/world/entity/decoration/ArmorStand;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V", at=@At(value="FIELD", target="Lnet/minecraft/world/entity/decoration/ArmorStand;lastHit:J"))
    private long replayWiggle(ArmorStand stand, Operation<Long> original) {
        return Flashback.isInReplay() ? stand.level().getGameTime() - 100 : original.call(stand);
    }
}
