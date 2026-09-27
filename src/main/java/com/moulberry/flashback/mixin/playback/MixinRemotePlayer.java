package com.moulberry.flashback.mixin.playback;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ext.RemotePlayerExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RemotePlayer.class)
public class MixinRemotePlayer extends AbstractClientPlayer implements RemotePlayerExt {

    @Unique
    private boolean wasSwinging = false;

    @Unique
    private float xBobO = 0.0f;
    @Unique
    private float xBob = 0.0f;
    @Unique
    private float yBobO = 0.0f;
    @Unique
    private float yBob = 0.0f;
    @Unique
    private Vec3 lastPosition = null;

    @Unique
    private ItemStack mainHandItem = ItemStack.EMPTY;
    @Unique
    private ItemStack offHandItem = ItemStack.EMPTY;
    @Unique
    private float mainHandHeight;
    @Unique
    private float oMainHandHeight;
    @Unique
    private float offHandHeight;
    @Unique
    private float oOffHandHeight;

    @Unique
    private Vec3 lastBoatPosition = null;
    @Unique
    private double lastBoatSpeed = 0.0f;

    private MixinRemotePlayer(ClientLevel clientLevel, GameProfile gameProfile) {
        super(clientLevel, gameProfile);
    }

    @Inject(method = "aiStep", at = @At("RETURN"))
    public void aiStep(CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            if (!this.wasSwinging && this.swinging) {
                this.resetAttackStrengthTicker();
            }
            this.wasSwinging = this.swinging;

            this.xBobO = xBob;
            this.xBob += Mth.wrapDegrees(this.getXRot() - this.xBob) * 0.5f;
            this.yBobO = yBob;
            this.yBob += Mth.wrapDegrees(this.getYRot() - this.yBob) * 0.5f;

            if (this.lastPosition != null && this.walkDistO == this.walkDist) {
                double dx = this.lastPosition.x - this.position().x;
                double dz = this.lastPosition.z - this.position().z;
                this.walkDist += (float) Math.sqrt(dx*dx + dz*dz) * 0.6f;
            }
            this.lastPosition = this.position();

            // Update held item
            this.oMainHandHeight = this.mainHandHeight;
            this.oOffHandHeight = this.offHandHeight;
            ItemStack nextMainHand = this.getMainHandItem();
            ItemStack nextOffHand = this.getOffhandItem();
            if (shouldInstantlyReplaceVisibleItem(this.mainHandItem, nextMainHand)) {
                this.mainHandItem = nextMainHand;
            }

            if (shouldInstantlyReplaceVisibleItem(this.offHandItem, nextOffHand)) {
                this.offHandItem = nextOffHand;
            }

            boolean handsBusy = false;
            if (this.getVehicle() instanceof Boat boat) {
                Vec3 boatPosition = boat.position();

                if (this.lastBoatPosition != null) {
                    double boatSpeed = boatPosition.distanceToSqr(this.lastBoatPosition);

                    handsBusy = this.lastBoatSpeed < boatSpeed || (this.lastBoatSpeed == boatSpeed && boatSpeed > 0);

                    this.lastBoatSpeed = boatSpeed;
                }

                this.lastBoatPosition = boatPosition;
            }

            if (handsBusy) {
                this.mainHandHeight = Mth.clamp(this.mainHandHeight - 0.4F, 0.0F, 1.0F);
                this.offHandHeight = Mth.clamp(this.offHandHeight - 0.4F, 0.0F, 1.0F);
            } else {
                float attackAnim = this.getAttackStrengthScale(1.0F);
                float mainHandTargetHeight = this.mainHandItem != nextMainHand ? 0.0F : attackAnim * attackAnim * attackAnim;
                float offHandTargetHeight = this.offHandItem != nextOffHand ? 0.0F : 1.0F;
                this.mainHandHeight += Mth.clamp(mainHandTargetHeight - this.mainHandHeight, -0.4F, 0.4F);
                this.offHandHeight += Mth.clamp(offHandTargetHeight - this.offHandHeight, -0.4F, 0.4F);
            }

            if (this.mainHandHeight < 0.1F) {
                this.mainHandItem = nextMainHand;
            }

            if (this.offHandHeight < 0.1F) {
                this.offHandItem = nextOffHand;
            }
        }
    }

    @Unique
    private static boolean shouldInstantlyReplaceVisibleItem(ItemStack current, ItemStack next) {
        return ItemStack.matches(current, next) || !net.minecraftforge.client.ForgeHooksClient.shouldCauseReequipAnimation(current, next, -1);
    }
    @Override public float flashback$getXBob(float tick) { return Mth.lerp(tick, xBobO, xBob); }
    @Override public float flashback$getYBob(float tick) { return Mth.lerp(tick, yBobO, yBob); }
    @Override public ItemStack flashback$getMainHand() { return mainHandItem; }
    @Override public ItemStack flashback$getOffHand() { return offHandItem; }
    @Override public float flashback$getMainHandHeight(float tick) { return Mth.lerp(tick, oMainHandHeight, mainHandHeight); }
    @Override public float flashback$getOffHandHeight(float tick) { return Mth.lerp(tick, oOffHandHeight, offHandHeight); }
}
