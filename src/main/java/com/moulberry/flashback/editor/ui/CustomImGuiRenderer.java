package com.moulberry.flashback.editor.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import imgui.moulberry90.ImDrawData;

public interface CustomImGuiRenderer {

    void init();
    RenderTarget renderDrawData(final ImDrawData drawData);
    long getTextureId(int textureId);
    void setSampleLinear(long id);
    void setSampleNearest(long id);
    void updateFontsTexture();

}
