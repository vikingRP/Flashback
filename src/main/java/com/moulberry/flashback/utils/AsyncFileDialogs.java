package com.moulberry.flashback.utils;

import com.moulberry.flashback.Flashback;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.nfd.NFDFilterItem;
import org.lwjgl.util.nfd.NativeFileDialog;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native dialogs use GLFW's UI thread on macOS and a dedicated worker elsewhere. */
public final class AsyncFileDialogs {
    private static final ExecutorService DIALOG_THREAD = Executors.newSingleThreadExecutor(new NamedDaemonThreadFactory("FlashbackFileDialogs"));
    private static volatile CompletableFuture<String> currentSaveOrOpenFileDialog;

    public static boolean hasDialog() { return currentSaveOrOpenFileDialog != null; }

    public static CompletableFuture<String> saveFileDialog(String path, String name, String description, String... filters) {
        return show(0, path, name, description, filters);
    }

    public static CompletableFuture<String> openFileDialog(String path, String description, String... filters) {
        return show(1, path, null, description, filters);
    }

    public static CompletableFuture<String> openFolderDialog(String path) {
        return show(2, path, null, null);
    }

    private static synchronized CompletableFuture<String> show(int kind, String path, String name, String description, String... filters) {
        if (hasDialog()) return CompletableFuture.completedFuture(null);
        CompletableFuture<String> future = new CompletableFuture<>();
        currentSaveOrOpenFileDialog = future;
        Runnable dialog = () -> {
            String selected = null;
            boolean initialized = false;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                if (NativeFileDialog.NFD_Init() != NativeFileDialog.NFD_OKAY) {
                    throw new IllegalStateException(NativeFileDialog.NFD_GetError());
                }
                initialized = true;
                PointerBuffer out = stack.callocPointer(1);
                NFDFilterItem.Buffer nativeFilters = null;
                if (filters.length > 0) {
                    nativeFilters = NFDFilterItem.calloc(1, stack);
                    nativeFilters.get(0).name(stack.UTF8(filter(description))).spec(stack.UTF8(filter(String.join(",", filters))));
                }
                int result = switch (kind) {
                    case 0 -> NativeFileDialog.NFD_SaveDialog(out, nativeFilters, filter(path), filter(name));
                    case 1 -> NativeFileDialog.NFD_OpenDialog(out, nativeFilters, filter(path));
                    default -> NativeFileDialog.NFD_PickFolder(out, filter(path));
                };
                if (result == NativeFileDialog.NFD_OKAY) {
                    try {
                        selected = out.getStringUTF8(0);
                    } finally {
                        NativeFileDialog.NFD_FreePath(out.get(0));
                    }
                    if (kind == 0 && filters.length == 1 && !Path.of(selected).getFileName().toString().contains(".")) {
                        selected += "." + filters[0];
                    }
                } else if (result == NativeFileDialog.NFD_ERROR) {
                    Flashback.LOGGER.error("Native file dialog failed: {}", NativeFileDialog.NFD_GetError());
                }
            } catch (Throwable error) {
                Flashback.LOGGER.error("Native file dialog failed", error);
            } finally {
                try {
                    if (initialized) NativeFileDialog.NFD_Quit();
                } finally {
                    completeOnClient(Minecraft.getInstance(), future, selected);
                }
            }
        };
        if (Util.getPlatform() == Util.OS.OSX) Minecraft.getInstance().execute(dialog);
        else DIALOG_THREAD.execute(dialog);
        return future;
    }

    /** Publish selection/cancellation only after returning to the client event loop. */
    static void completeOnClient(Executor client, CompletableFuture<String> future, String selected) {
        client.execute(() -> {
            if (currentSaveOrOpenFileDialog == future) currentSaveOrOpenFileDialog = null;
            future.complete(selected);
        });
    }

    public static String filter(CharSequence in) {
        return in == null ? "" : filterLT20(in);
    }

    public static String filterLT20(CharSequence in) {
        StringBuilder result = new StringBuilder();
        for (int i=0; i<in.length(); i++) {
            char c = in.charAt(i);
            if (c >= 32 || c == '\n') result.append(c);
        }
        return result.toString();
    }
}
