package com.moulberry.flashback.mixin.visuals;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class MixinAbstractClientPlayer {
    @Shadow protected abstract PlayerInfo getPlayerInfo();
    @Unique private PlayerInfo flashback$fallback;
    @Unique private PlayerInfo flashback$override;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void flashback$init(ClientLevel level, GameProfile profile, CallbackInfo ci) {
        if (Flashback.isInReplay()) flashback$fallback = new PlayerInfo(profile, false);
    }

    @Inject(method = "getPlayerInfo", at = @At("RETURN"), cancellable = true)
    private void flashback$fallbackInfo(CallbackInfoReturnable<PlayerInfo> cir) {
        if (cir.getReturnValue() == null && flashback$fallback != null) cir.setReturnValue(flashback$fallback);
    }

    @Unique private PlayerInfo flashback$overrideInfo() {
        var state = EditorStateManager.getCurrent();
        if (state == null) return null;
        GameProfile profile = state.skinOverride.get(((AbstractClientPlayer)(Object)this).getUUID());
        if (profile == null) return null;
        if (flashback$override == null || flashback$override.getProfile() != profile) flashback$override = new PlayerInfo(profile, false);
        return flashback$override;
    }

    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void flashback$skinTexture(CallbackInfoReturnable<ResourceLocation> cir) {
        var state = EditorStateManager.getCurrent();
        if (state == null) return;
        var file = state.skinOverrideFromFile.get(((AbstractClientPlayer)(Object)this).getUUID());
        if (file != null) cir.setReturnValue(file.getSkin().texture());
        else { var info = flashback$overrideInfo(); if (info != null) cir.setReturnValue(info.getSkinLocation()); }
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void flashback$skinModel(CallbackInfoReturnable<String> cir) {
        var state = EditorStateManager.getCurrent();
        if (state == null) return;
        var file = state.skinOverrideFromFile.get(((AbstractClientPlayer)(Object)this).getUUID());
        if (file != null) cir.setReturnValue(file.getSkin().model());
        else { var info = flashback$overrideInfo(); if (info != null) cir.setReturnValue(info.getModelName()); }
    }

    @Inject(method = "getCloakTextureLocation", at = @At("HEAD"), cancellable = true)
    private void flashback$cape(CallbackInfoReturnable<ResourceLocation> cir) {
        var state = EditorStateManager.getCurrent();
        if (state == null) return;
        var uuid = ((AbstractClientPlayer)(Object)this).getUUID();
        if (state.hideCape.contains(uuid) || state.skinOverrideFromFile.containsKey(uuid)) cir.setReturnValue(null);
        else { var info = flashback$overrideInfo(); if (info != null) cir.setReturnValue(info.getCapeLocation()); }
    }

    @Inject(method = "getElytraTextureLocation", at = @At("HEAD"), cancellable = true)
    private void flashback$elytra(CallbackInfoReturnable<ResourceLocation> cir) {
        var state = EditorStateManager.getCurrent();
        if (state == null) return;
        if (state.skinOverrideFromFile.containsKey(((AbstractClientPlayer)(Object)this).getUUID())) cir.setReturnValue(null);
        else { var info = flashback$overrideInfo(); if (info != null) cir.setReturnValue(info.getElytraLocation()); }
    }
}
