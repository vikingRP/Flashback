package com.moulberry.flashback.utils;

import com.moulberry.flashback.editor.ui.CustomImGuiImplGlfw;
import imgui.moulberry90.flag.ImGuiKey;
import net.minecraft.client.Minecraft;
import static org.lwjgl.glfw.GLFW.*;

public final class InputHelper {
    public static final int EDIT_SHORTCUT_KEY_LEFT = Minecraft.ON_OSX ? GLFW_KEY_LEFT_SUPER : GLFW_KEY_LEFT_CONTROL;
    public static final int EDIT_SHORTCUT_KEY_RIGHT = Minecraft.ON_OSX ? GLFW_KEY_RIGHT_SUPER : GLFW_KEY_RIGHT_CONTROL;
    private static boolean pressed(int key) { return glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), key) == GLFW_PRESS; }
    public static boolean isCtrlOrCmdDownRaw() { return pressed(EDIT_SHORTCUT_KEY_LEFT) || pressed(EDIT_SHORTCUT_KEY_RIGHT); }
    public static boolean isCtrlDownRaw() { return pressed(GLFW_KEY_LEFT_CONTROL) || pressed(GLFW_KEY_RIGHT_CONTROL); }
    public static boolean isShiftDownRaw() { return pressed(GLFW_KEY_LEFT_SHIFT) || pressed(GLFW_KEY_RIGHT_SHIFT); }
    public static boolean isAltDownRaw() { return pressed(GLFW_KEY_LEFT_ALT) || pressed(GLFW_KEY_RIGHT_ALT); }
    public static boolean isSuperDownRaw() { return pressed(GLFW_KEY_LEFT_SUPER) || pressed(GLFW_KEY_RIGHT_SUPER); }
    public static int imguiKeyToGlfw(int imguiKey) {
        if (imguiKey == ImGuiKey.None) return GLFW_KEY_UNKNOWN;
        for (int key = GLFW_KEY_SPACE; key <= GLFW_KEY_LAST; key++)
            if (CustomImGuiImplGlfw.glfwKeyToImGuiKey(key) == imguiKey) return key;
        return GLFW_KEY_UNKNOWN;
    }
    public static boolean isKeyDownRaw(int imguiKey) {
        int key = imguiKeyToGlfw(imguiKey);
        return key != GLFW_KEY_UNKNOWN && pressed(key);
    }
    public static int glfwScancodeToImguiKey(int scancode) {
        if (scancode < 0) return ImGuiKey.None;
        for (int key = GLFW_KEY_SPACE; key <= GLFW_KEY_LAST; key++) {
            int imguiKey = CustomImGuiImplGlfw.glfwKeyToImGuiKey(key);
            if (imguiKey != ImGuiKey.None && glfwGetKeyScancode(key) == scancode) return imguiKey;
        }
        return ImGuiKey.None;
    }
    public static boolean isMouseDownRaw(int button) {
        return button >= 0 && button <= GLFW_MOUSE_BUTTON_LAST &&
            glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), button) == GLFW_PRESS;
    }
    public static int sdlMouseToImguiMouse(int button) {
        return switch (button) { case 1 -> 0; case 3 -> 1; case 2 -> 2; case 4 -> 3; case 5 -> 4; default -> -1; };
    }
    public static int sdlScancodeToImguiKey(int sdlScancode) {
        return switch (sdlScancode) {
            case 43 -> ImGuiKey.Tab;
            case 80 -> ImGuiKey.LeftArrow;
            case 79 -> ImGuiKey.RightArrow;
            case 82 -> ImGuiKey.UpArrow;
            case 81 -> ImGuiKey.DownArrow;
            case 75 -> ImGuiKey.PageUp;
            case 78 -> ImGuiKey.PageDown;
            case 74 -> ImGuiKey.Home;
            case 77 -> ImGuiKey.End;
            case 73 -> ImGuiKey.Insert;
            case 76 -> ImGuiKey.Delete;
            case 42 -> ImGuiKey.Backspace;
            case 44 -> ImGuiKey.Space;
            case 40 -> ImGuiKey.Enter;
            case 41 -> ImGuiKey.Escape;
            case 224 -> ImGuiKey.LeftCtrl;
            case 225 -> ImGuiKey.LeftShift;
            case 226 -> ImGuiKey.LeftAlt;
            case 227 -> ImGuiKey.LeftSuper;
            case 228 -> ImGuiKey.RightCtrl;
            case 229 -> ImGuiKey.RightShift;
            case 230 -> ImGuiKey.RightAlt;
            case 231 -> ImGuiKey.RightSuper;
            case 101 -> ImGuiKey.Menu;
            case 39 -> ImGuiKey._0;
            case 30 -> ImGuiKey._1;
            case 31 -> ImGuiKey._2;
            case 32 -> ImGuiKey._3;
            case 33 -> ImGuiKey._4;
            case 34 -> ImGuiKey._5;
            case 35 -> ImGuiKey._6;
            case 36 -> ImGuiKey._7;
            case 37 -> ImGuiKey._8;
            case 38 -> ImGuiKey._9;
            case 4 -> ImGuiKey.A;
            case 5 -> ImGuiKey.B;
            case 6 -> ImGuiKey.C;
            case 7 -> ImGuiKey.D;
            case 8 -> ImGuiKey.E;
            case 9 -> ImGuiKey.F;
            case 10 -> ImGuiKey.G;
            case 11 -> ImGuiKey.H;
            case 12 -> ImGuiKey.I;
            case 13 -> ImGuiKey.J;
            case 14 -> ImGuiKey.K;
            case 15 -> ImGuiKey.L;
            case 16 -> ImGuiKey.M;
            case 17 -> ImGuiKey.N;
            case 18 -> ImGuiKey.O;
            case 19 -> ImGuiKey.P;
            case 20 -> ImGuiKey.Q;
            case 21 -> ImGuiKey.R;
            case 22 -> ImGuiKey.S;
            case 23 -> ImGuiKey.T;
            case 24 -> ImGuiKey.U;
            case 25 -> ImGuiKey.V;
            case 26 -> ImGuiKey.W;
            case 27 -> ImGuiKey.X;
            case 28 -> ImGuiKey.Y;
            case 29 -> ImGuiKey.Z;
            case 58 -> ImGuiKey.F1;
            case 59 -> ImGuiKey.F2;
            case 60 -> ImGuiKey.F3;
            case 61 -> ImGuiKey.F4;
            case 62 -> ImGuiKey.F5;
            case 63 -> ImGuiKey.F6;
            case 64 -> ImGuiKey.F7;
            case 65 -> ImGuiKey.F8;
            case 66 -> ImGuiKey.F9;
            case 67 -> ImGuiKey.F10;
            case 68 -> ImGuiKey.F11;
            case 69 -> ImGuiKey.F12;
            case 104 -> ImGuiKey.F13;
            case 105 -> ImGuiKey.F14;
            case 106 -> ImGuiKey.F15;
            case 107 -> ImGuiKey.F16;
            case 108 -> ImGuiKey.F17;
            case 109 -> ImGuiKey.F18;
            case 110 -> ImGuiKey.F19;
            case 111 -> ImGuiKey.F20;
            case 112 -> ImGuiKey.F21;
            case 113 -> ImGuiKey.F22;
            case 114 -> ImGuiKey.F23;
            case 115 -> ImGuiKey.F24;
            case 52 -> ImGuiKey.Apostrophe;
            case 54 -> ImGuiKey.Comma;
            case 45 -> ImGuiKey.Minus;
            case 55 -> ImGuiKey.Period;
            case 56 -> ImGuiKey.Slash;
            case 51 -> ImGuiKey.Semicolon;
            case 46 -> ImGuiKey.Equal;
            case 47 -> ImGuiKey.LeftBracket;
            case 49 -> ImGuiKey.Backslash;
            case 48 -> ImGuiKey.RightBracket;
            case 53 -> ImGuiKey.GraveAccent;
            case 57 -> ImGuiKey.CapsLock;
            case 71 -> ImGuiKey.ScrollLock;
            case 83 -> ImGuiKey.NumLock;
            case 70 -> ImGuiKey.PrintScreen;
            case 72 -> ImGuiKey.Pause;
            case 98 -> ImGuiKey.Keypad0;
            case 89 -> ImGuiKey.Keypad1;
            case 90 -> ImGuiKey.Keypad2;
            case 91 -> ImGuiKey.Keypad3;
            case 92 -> ImGuiKey.Keypad4;
            case 93 -> ImGuiKey.Keypad5;
            case 94 -> ImGuiKey.Keypad6;
            case 95 -> ImGuiKey.Keypad7;
            case 96 -> ImGuiKey.Keypad8;
            case 97 -> ImGuiKey.Keypad9;
            case 220 -> ImGuiKey.KeypadDecimal;
            case 84 -> ImGuiKey.KeypadDivide;
            case 85 -> ImGuiKey.KeypadMultiply;
            case 86 -> ImGuiKey.KeypadSubtract;
            case 87 -> ImGuiKey.KeypadAdd;
            case 88 -> ImGuiKey.KeypadEnter;
            case 103 -> ImGuiKey.KeypadEqual;
            case 270 -> ImGuiKey.AppBack;
            case 271 -> ImGuiKey.AppForward;
            default -> ImGuiKey.None;
        };
    }

}
