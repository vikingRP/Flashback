package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.ReplayMarker;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class WorldRenderHook {
    private static final RenderType MARKER_CIRCLE = RenderType.text(new ResourceLocation("flashback", "world_marker_circle.png"));

    public static void renderHook(PoseStack poseStack, Camera camera) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer == null || Flashback.isExporting() || !ReplayUI.isActive()) return;
        EditorState state = EditorStateManager.getCurrent();
        if (state != null && state.replayVisuals.cameraPath)
            CameraPath.renderCameraPath(poseStack, camera, replayServer);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        String dimension = minecraft.level.dimension().toString();
        for (ReplayMarker marker : replayServer.getMetadata().replayMarkers.values()) {
            if (marker.position() == null || !marker.position().dimension().equals(dimension)) continue;
            var position = marker.position().position();
            poseStack.pushPose();
            poseStack.translate(position.x - camera.getPosition().x, position.y - camera.getPosition().y, position.z - camera.getPosition().z);
            poseStack.mulPose(camera.rotation());
            Matrix4f matrix = poseStack.last().pose();
            VertexConsumer vertices = buffers.getBuffer(MARKER_CIRCLE);
            int colour = marker.colour() | 0xff000000;
            vertices.vertex(matrix, -0.2f, -0.2f, 0).color(colour).uv(0, 0).uv2(15728880).endVertex();
            vertices.vertex(matrix, 0.2f, -0.2f, 0).color(colour).uv(1, 0).uv2(15728880).endVertex();
            vertices.vertex(matrix, 0.2f, 0.2f, 0).color(colour).uv(1, 1).uv2(15728880).endVertex();
            vertices.vertex(matrix, -0.2f, 0.2f, 0).color(colour).uv(0, 1).uv2(15728880).endVertex();
            if (marker.description() != null) {
                poseStack.scale(-0.025f, -0.025f, 0.025f);
                minecraft.font.drawInBatch(marker.description(), -minecraft.font.width(marker.description()) / 2f, -20,
                    -1, true, poseStack.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, 15728880);
            }
            poseStack.popPose();
        }
        buffers.endBatch();
    }
}
