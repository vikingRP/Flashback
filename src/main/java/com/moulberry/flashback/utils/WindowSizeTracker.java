package com.moulberry.flashback.utils;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

public class WindowSizeTracker {

    /**
     * Some mods (e.g. qdaa) like to manipulate the framebuffer width
     * This can cause rendering issues since we don't know the real width
     * This helper will cache and calculate the real framebuffer width to avoid this issue
     */

    private static int lastFramebufferWidth = -1;
    private static int lastFramebufferHeight = -1;
    private static int realFramebufferWidth;
    private static int realFramebufferHeight;

    public static int getWidth(Window window) {
        if (lastFramebufferWidth != window.framebufferWidth) {
            recalculate(window);
        }

        return realFramebufferWidth;
    }


    public static int getHeight(Window window) {
        if (lastFramebufferHeight != window.framebufferHeight) {
            recalculate(window);
        }

        return realFramebufferHeight;
    }

    private static void recalculate(Window window) {
        // Calculate real framebuffer width/height
        int[] width = new int[1];
        int[] height = new int[1];
        getFramebufferSizeRaw(window.getWindow(), width, height);
        realFramebufferWidth = Math.max(1, width[0]);
        realFramebufferHeight = Math.max(1, height[0]);

        // Update cached values
        lastFramebufferWidth = window.framebufferWidth;
        lastFramebufferHeight = window.framebufferHeight;
    }

    public static void getFramebufferSizeRaw(long handle, int[] width, int[] height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer widthBuf = stack.callocInt(1);
            IntBuffer heightBuf = stack.callocInt(1);
            GLFW.glfwGetFramebufferSize(handle, widthBuf, heightBuf);
            width[0] = widthBuf.get();
            height[0] = heightBuf.get();
        }
    }

    public static void getWindowSizeRaw(long handle, int[] width, int[] height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer widthBuf = stack.callocInt(1);
            IntBuffer heightBuf = stack.callocInt(1);
            GLFW.glfwGetWindowSize(handle, widthBuf, heightBuf);
            width[0] = widthBuf.get();
            height[0] = heightBuf.get();
        }
    }

}
