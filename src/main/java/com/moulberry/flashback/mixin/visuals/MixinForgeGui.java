package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.platform.FlashbackHud;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ForgeGui.class, remap = false)
public abstract class MixinForgeGui {
    @Inject(method = "shouldDrawSurvivalElements", at = @At("HEAD"), cancellable = true)
    private void flashback$survivalHud(CallbackInfoReturnable<Boolean> cir) {
        var mode = FlashbackHud.cameraGameType();
        if (mode != null) cir.setReturnValue(mode.isSurvival());
    }
    @Inject(method = "renderExperience", at = @At("HEAD"), cancellable = true)
    private void flashback$experience(int x, GuiGraphics graphics, CallbackInfo ci) {
        var mode = FlashbackHud.cameraGameType();
        if (mode != null) {
            if (mode.isSurvival()) {
                graphics.setColor(1, 1, 1, 1);
                RenderSystem.disableBlend();
                Minecraft.getInstance().gui.renderExperienceBar(graphics, x);
                RenderSystem.enableBlend();
            }
            ci.cancel();
        }
    }
    @WrapOperation(method = "renderArmor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getArmorValue()I", remap = true))
    private int flashback$armor(LocalPlayer instance, Operation<Integer> original) {
        if (Flashback.isInReplay() && Minecraft.getInstance().getCameraEntity() instanceof Player player) return player.getArmorValue();
        return original.call(instance);
    }
    @WrapOperation(method = "renderFood", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getFoodData()Lnet/minecraft/world/food/FoodData;", remap = true))
    private FoodData flashback$food(LocalPlayer instance, Operation<FoodData> original) {
        if (Flashback.isInReplay() && Minecraft.getInstance().getCameraEntity() instanceof Player player) return player.getFoodData();
        return original.call(instance);
    }
    @WrapOperation(method = "renderFood", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/world/effect/MobEffect;)Z", remap = true))
    private boolean flashback$hunger(LocalPlayer instance, MobEffect effect, Operation<Boolean> original) {
        if (Flashback.isInReplay() && Minecraft.getInstance().getCameraEntity() instanceof Player player) return player.hasEffect(effect);
        return original.call(instance, effect);
    }
}
