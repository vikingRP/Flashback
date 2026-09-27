package com.moulberry.flashback.mixin.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// In 1.20.1 the sky, sun, moon and star brightness read the time through LevelAccessor#dayTime,
// which bypasses Level#getDayTime, so the override is applied where that time is consumed.
// Only the render thread is affected, leaving the replay server's own time untouched
@Mixin(DimensionType.class)
public class MixinDimensionType {

    @ModifyVariable(method = {"timeOfDay", "moonPhase"}, at = @At("HEAD"), argsOnly = true)
    public long overrideDayTime(long dayTime) {
        if (!RenderSystem.isOnRenderThread()) {
            return dayTime;
        }
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.overrideTimeOfDay >= 0) {
            return editorState.replayVisuals.overrideTimeOfDay;
        }
        return dayTime;
    }

}
