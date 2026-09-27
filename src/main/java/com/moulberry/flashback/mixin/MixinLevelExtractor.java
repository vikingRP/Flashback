package com.moulberry.flashback.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.renderer.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person overlays for the recorded camera player on the 1.20.1 renderer. */
@Mixin(ScreenEffectRenderer.class)
public abstract class MixinLevelExtractor {
    @Shadow private static void renderTex(TextureAtlasSprite sprite, PoseStack pose) { throw new AssertionError(); }
    @Shadow private static void renderFire(Minecraft minecraft, PoseStack pose) { throw new AssertionError(); }
    @Inject(method = "renderScreenEffect", at = @At("HEAD"), cancellable = true)
   private static void flashback$screenEffects(Minecraft p_110719_, PoseStack p_110720_, CallbackInfo ci) {
      Player player = Flashback.getSpectatingPlayer();
      if (player == null) return;
      ci.cancel();
      if (player.isSleeping()) return;
      if (!player.noPhysics) {
         org.apache.commons.lang3.tuple.Pair<BlockState, BlockPos> overlay = flashback$getOverlayBlock(player);
         if (overlay != null) {
            if (!net.minecraftforge.client.ForgeHooksClient.renderBlockOverlay(player, p_110720_, net.minecraftforge.client.event.RenderBlockScreenEffectEvent.OverlayType.BLOCK, overlay.getLeft(), overlay.getRight()))
               renderTex(p_110719_.getBlockRenderer().getBlockModelShaper().getTexture(overlay.getLeft(), p_110719_.level, overlay.getRight()), p_110720_);
         }
      }

      if (!player.isSpectator()) {
         if (player.isEyeInFluid(FluidTags.WATER)) {
            if (!net.minecraftforge.client.ForgeHooksClient.renderWaterOverlay(player, p_110720_))
            flashback$renderFluid(player, p_110720_, new ResourceLocation("textures/misc/underwater.png"));
         }
         else if (!player.getEyeInFluidType().isAir()) net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(player.getEyeInFluidType()).renderOverlay(p_110719_, p_110720_);

         if (player.isOnFire()) {
            if (!net.minecraftforge.client.ForgeHooksClient.renderFireOverlay(player, p_110720_))
            renderFire(p_110719_, p_110720_);
         }
      }

   }

   @Unique
   @Nullable
   private static org.apache.commons.lang3.tuple.Pair<BlockState, BlockPos> flashback$getOverlayBlock(Player p_110717_) {
      BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

      for(int i = 0; i < 8; ++i) {
         double d0 = p_110717_.getX() + (double)(((float)((i >> 0) % 2) - 0.5F) * p_110717_.getBbWidth() * 0.8F);
         double d1 = p_110717_.getEyeY() + (double)(((float)((i >> 1) % 2) - 0.5F) * 0.1F);
         double d2 = p_110717_.getZ() + (double)(((float)((i >> 2) % 2) - 0.5F) * p_110717_.getBbWidth() * 0.8F);
         blockpos$mutableblockpos.set(d0, d1, d2);
         BlockState blockstate = p_110717_.level().getBlockState(blockpos$mutableblockpos);
         if (blockstate.getRenderShape() != RenderShape.INVISIBLE && blockstate.isViewBlocking(p_110717_.level(), blockpos$mutableblockpos)) {
            return org.apache.commons.lang3.tuple.Pair.of(blockstate, blockpos$mutableblockpos.immutable());
         }
      }

      return null;
   }

    @Unique
   private static void flashback$renderFluid(Player player, PoseStack p_110727_, ResourceLocation texture) {
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      RenderSystem.setShaderTexture(0, texture);
      BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
      BlockPos blockpos = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
      float f = LightTexture.getBrightness(player.level().dimensionType(), player.level().getMaxLocalRawBrightness(blockpos));
      RenderSystem.enableBlend();
      RenderSystem.setShaderColor(f, f, f, 0.1F);
      float f1 = 4.0F;
      float f2 = -1.0F;
      float f3 = 1.0F;
      float f4 = -1.0F;
      float f5 = 1.0F;
      float f6 = -0.5F;
      float f7 = -player.getYRot() / 64.0F;
      float f8 = player.getXRot() / 64.0F;
      Matrix4f matrix4f = p_110727_.last().pose();
      bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
      bufferbuilder.vertex(matrix4f, -1.0F, -1.0F, -0.5F).uv(4.0F + f7, 4.0F + f8).endVertex();
      bufferbuilder.vertex(matrix4f, 1.0F, -1.0F, -0.5F).uv(0.0F + f7, 4.0F + f8).endVertex();
      bufferbuilder.vertex(matrix4f, 1.0F, 1.0F, -0.5F).uv(0.0F + f7, 0.0F + f8).endVertex();
      bufferbuilder.vertex(matrix4f, -1.0F, 1.0F, -0.5F).uv(4.0F + f7, 0.0F + f8).endVertex();
      BufferUploader.drawWithShader(bufferbuilder.end());
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.disableBlend();
   }

}
