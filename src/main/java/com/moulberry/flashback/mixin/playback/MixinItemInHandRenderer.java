package com.moulberry.flashback.mixin.playback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceLocation;
import com.google.common.base.MoreObjects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ext.RemotePlayerExt;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer {

    @Shadow
    private float oMainHandHeight;

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oOffHandHeight;

    @Shadow
    private float offHandHeight;

    @Shadow
    protected abstract void renderArmWithItem(AbstractClientPlayer abstractClientPlayer, float f, float g, InteractionHand interactionHand, float h, ItemStack itemStack, float i, PoseStack poseStack, MultiBufferSource multiBufferSource, int j);

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Shadow
    private static boolean isChargedCrossbow(ItemStack itemStack) {
        return false;
    }

    @Unique
    private static final int RENDER_MAIN_HAND = 1;
    @Unique
    private static final int RENDER_OFF_HAND = 2;
    @Unique
    private static final int RENDER_BOTH_HANDS = RENDER_MAIN_HAND | RENDER_OFF_HAND;

    @Unique
    private static int evaluateWhichHandsToRender(AbstractClientPlayer player) {
        ItemStack mainStack = player.getMainHandItem();
        ItemStack offStack = player.getOffhandItem();
        boolean isHoldingBow = mainStack.is(Items.BOW) || offStack.is(Items.BOW);
        boolean isHoldingCrossbow = mainStack.is(Items.CROSSBOW) || offStack.is(Items.CROSSBOW);
        if (!isHoldingBow && !isHoldingCrossbow) {
            return RENDER_BOTH_HANDS;
        }
        if (player.isUsingItem()) {
            ItemStack useStack = player.getUseItem();
            InteractionHand interactionHand = player.getUsedItemHand();
            if (!useStack.is(Items.BOW) && !useStack.is(Items.CROSSBOW)) {
                return interactionHand == InteractionHand.MAIN_HAND && isChargedCrossbow(player.getOffhandItem()) ? RENDER_MAIN_HAND : RENDER_BOTH_HANDS;
            } else {
                return interactionHand == InteractionHand.MAIN_HAND ? RENDER_MAIN_HAND : RENDER_OFF_HAND;
            }
        }
        if (isChargedCrossbow(mainStack)) {
            return RENDER_MAIN_HAND;
        }
        return RENDER_BOTH_HANDS;
    }

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void flashback$spectatorHands(float partialTick, PoseStack pose, MultiBufferSource.BufferSource buffers, LocalPlayer local, int light, CallbackInfo ci) {
        AbstractClientPlayer player = Flashback.getSpectatingPlayer();
        if (player == null) return;
        ItemStack savedMain = mainHandItem, savedOff = offHandItem;
        float savedMainHeight = mainHandHeight, savedOldMain = oMainHandHeight, savedOffHeight = offHandHeight, savedOldOff = oOffHandHeight;
        try {
            flashback$renderHandsWithItems(partialTick, pose, buffers, player,
                net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getPackedLightCoords(player, partialTick));
        } finally {
            mainHandItem = savedMain; offHandItem = savedOff;
            mainHandHeight = savedMainHeight; oMainHandHeight = savedOldMain;
            offHandHeight = savedOffHeight; oOffHandHeight = savedOldOff;
        }
        ci.cancel();
    }

    public void flashback$renderHandsWithItems(float partialTick, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, AbstractClientPlayer clientPlayer, int i) {
        float m;
        float l;
        float g = clientPlayer.getAttackAnim(partialTick);
        InteractionHand interactionHand = MoreObjects.firstNonNull(clientPlayer.swingingArm, InteractionHand.MAIN_HAND);
        float h = Mth.lerp(partialTick, clientPlayer.xRotO, clientPlayer.getXRot());
         int handRenderSelection = evaluateWhichHandsToRender(clientPlayer);
        if (clientPlayer instanceof RemotePlayerExt remotePlayerExt) {
            this.mainHandItem = remotePlayerExt.flashback$getMainHand();
            this.offHandItem = remotePlayerExt.flashback$getOffHand();
            this.oMainHandHeight = this.mainHandHeight = remotePlayerExt.flashback$getMainHandHeight(partialTick);
            this.oOffHandHeight = this.offHandHeight = remotePlayerExt.flashback$getOffHandHeight(partialTick);
            poseStack.mulPose(Axis.XP.rotationDegrees((clientPlayer.getViewXRot(partialTick) - remotePlayerExt.flashback$getXBob(partialTick)) * 0.1f));
            poseStack.mulPose(Axis.YP.rotationDegrees((clientPlayer.getViewYRot(partialTick) - remotePlayerExt.flashback$getYBob(partialTick)) * 0.1f));
        }

        if ((handRenderSelection & RENDER_MAIN_HAND) != 0) {
            l = interactionHand == InteractionHand.MAIN_HAND ? g : 0.0f;
            m = 1.0f - Mth.lerp(partialTick, this.oMainHandHeight, this.mainHandHeight);
            renderArmWithItem(clientPlayer, partialTick, h, InteractionHand.MAIN_HAND, l, this.mainHandItem, m, poseStack, bufferSource, i);
        }
        if ((handRenderSelection & RENDER_OFF_HAND) != 0) {
            l = interactionHand == InteractionHand.OFF_HAND ? g : 0.0f;
            m = 1.0f - Mth.lerp(partialTick, this.oOffHandHeight, this.offHandHeight);
            renderArmWithItem(clientPlayer, partialTick, h, InteractionHand.OFF_HAND, l, this.offHandItem, m, poseStack, bufferSource, i);
        }
        bufferSource.endBatch();
    }

    @ModifyArg(method = {"renderPlayerArm", "renderMapHand"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;"))
    public Entity renderPlayerArm_getRenderer(Entity entity) {
        AbstractClientPlayer spectatingPlayer = Flashback.getSpectatingPlayer();
        if (spectatingPlayer != null) {
            return spectatingPlayer;
        }
        return entity;
    }

    @ModifyArg(method = {"renderPlayerArm", "renderMapHand"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/player/PlayerRenderer;renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V"))
    public AbstractClientPlayer renderPlayerArm_renderLeftHand(AbstractClientPlayer entity) {
        AbstractClientPlayer spectatingPlayer = Flashback.getSpectatingPlayer();
        if (spectatingPlayer != null) {
            return spectatingPlayer;
        }
        return entity;
    }

    @ModifyArg(method = {"renderPlayerArm", "renderMapHand"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/player/PlayerRenderer;renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V"))
    public AbstractClientPlayer renderPlayerArm_renderRightHand(AbstractClientPlayer entity) {
        AbstractClientPlayer spectatingPlayer = Flashback.getSpectatingPlayer();
        if (spectatingPlayer != null) {
            return spectatingPlayer;
        }
        return entity;
    }


    @WrapOperation(method = {"applyEatTransform", "applyBrushTransform", "renderArmWithItem"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getUseItemRemainingTicks()I"))
    private int flashback$recordedUseTicks(LocalPlayer player, Operation<Integer> original) {
        var recorded = Flashback.getSpectatingPlayer();
        return recorded == null ? original.call(player) : recorded.getUseItemRemainingTicks();
    }

    @WrapOperation(method = {"renderOneHandedMap", "renderTwoHandedMap"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isInvisible()Z"))
    private boolean flashback$recordedInvisible(LocalPlayer player, Operation<Boolean> original) {
        var recorded = Flashback.getSpectatingPlayer();
        return recorded == null ? original.call(player) : recorded.isInvisible();
    }

    @WrapOperation(method = "renderMapHand",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getSkinTextureLocation()Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation flashback$mapSkin(LocalPlayer player, Operation<ResourceLocation> original) {
        var recorded = Flashback.getSpectatingPlayer();
        return recorded == null ? original.call(player) : recorded.getSkinTextureLocation();
    }

    @WrapOperation(method = "renderPlayerArm",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getSkinTextureLocation()Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation flashback$armSkin(AbstractClientPlayer player, Operation<ResourceLocation> original) {
        var recorded = Flashback.getSpectatingPlayer();
        return recorded == null ? original.call(player) : recorded.getSkinTextureLocation();
    }
}
