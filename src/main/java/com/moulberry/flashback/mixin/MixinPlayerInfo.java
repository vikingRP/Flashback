package com.moulberry.flashback.mixin;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PlayerInfo.class)
public class MixinPlayerInfo {
    @ModifyArg(method = "registerTextures", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/resources/SkinManager;registerSkins(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/client/resources/SkinManager$SkinTextureCallback;Z)V"), index = 2)
    private boolean flashback$allowRecordedSkins(boolean requireSecure) {
        return requireSecure && !Flashback.isInReplay();
    }
}
