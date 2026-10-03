package com.moulberry.flashback.exporting;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.VideoContainer;

import java.util.concurrent.CompletableFuture;

/** Detects native encoder support once per session, away from the render thread. */
public record ExportCapabilities(VideoContainer[] containers, VideoContainer[] transparentContainers) {
    private static CompletableFuture<ExportCapabilities> detection;

    public static synchronized CompletableFuture<ExportCapabilities> load() {
        if (detection == null) {
            detection = CompletableFuture.supplyAsync(ExportCapabilities::detect, task -> {
                Thread thread = new Thread(task, "Flashback encoder detection");
                thread.setDaemon(true);
                thread.start();
            }).whenComplete((result, error) -> {
                if (error != null) {
                    Flashback.LOGGER.error("Failed to detect export encoders", error);
                }
            });
        }
        return detection;
    }

    public static synchronized void retry() {
        if (detection != null && detection.isCompletedExceptionally()) {
            detection = null;
        }
        load();
    }

    private static ExportCapabilities detect() {
        long started = System.nanoTime();
        VideoContainer[] containers = VideoContainer.findSupportedContainers(false);
        VideoContainer[] transparentContainers = VideoContainer.findSupportedContainers(true);
        // Warm every audio/container combination before publishing to the UI. Switching
        // formats or enabling transparency must not trigger another native probe there.
        for (VideoContainer container : VideoContainer.values()) {
            container.getSupportedAudioCodecs();
        }
        Flashback.LOGGER.info("Export encoder detection completed in {} ms", (System.nanoTime() - started) / 1_000_000);
        return new ExportCapabilities(containers, transparentContainers);
    }
}
