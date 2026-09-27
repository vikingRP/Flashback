package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.entity.Entity;
import java.util.Objects;
import java.util.UUID;

public final class FlashbackDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;
    public FlashbackDebugRenderer(Minecraft minecraft) { this.minecraft = minecraft; }

    public void render(PoseStack poseStack, MultiBufferSource buffers, double x, double y, double z) {
        if (!Flashback.isInReplay() || minecraft.level == null || Flashback.isExporting() || !ReplayUI.isActive()) return;
        var state = EditorStateManager.getCurrent();
        if (state == null) return;
        UUID selected = ReplayUI.getSelectedEntity();
        if (selected != null) boundingBox(poseStack, buffers, Utils.getEntityByUuid(minecraft.level, selected), 0xffffff00, x, y, z);
        UUID audio = state.audioSourceEntity;
        if (audio != null && !Objects.equals(audio, selected))
            boundingBox(poseStack, buffers, Utils.getEntityByUuid(minecraft.level, audio), 0xff00ffff, x, y, z);
        if (state.maybeHasHiddenEntities())
            for (Entity entity : minecraft.level.entitiesForRendering())
                if (state.isEntityHidden(entity) && entity != minecraft.player)
                    boundingBox(poseStack, buffers, entity, 0x30ffffff, x, y, z);
    }

    private void boundingBox(PoseStack poseStack, MultiBufferSource buffers, Entity entity, int colour, double x, double y, double z) {
        if (entity == null) return;
        var delta = entity.getPosition(minecraft.getFrameTime()).subtract(entity.position());
        var box = entity.getBoundingBox().move(delta).move(-x, -y, -z);
        LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), box,
            ((colour >>> 16) & 255) / 255f, ((colour >>> 8) & 255) / 255f, (colour & 255) / 255f, (colour >>> 24) / 255f);
    }
}
