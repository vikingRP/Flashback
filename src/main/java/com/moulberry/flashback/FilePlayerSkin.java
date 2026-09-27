package com.moulberry.flashback;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import com.moulberry.flashback.platform.PlayerSkin;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class FilePlayerSkin {

    private static class CleanState implements Runnable {
        private ResourceLocation skinIdentifier;

        public CleanState(ResourceLocation skinIdentifier) {
            this.skinIdentifier = skinIdentifier;
        }

        @Override
        public void run() {
            if (this.skinIdentifier != null) {
                ResourceLocation toClean = this.skinIdentifier;
                Minecraft.getInstance().execute(() -> {
                    Flashback.LOGGER.info("Cleaning player skin {} because it's no longer in use!", toClean);
                    Minecraft.getInstance().getTextureManager().release(toClean);
                });
                this.skinIdentifier = null;
            }
        }
    }

    private transient PlayerSkin playerSkin = null;
    private final String pathToSkin;

    public FilePlayerSkin(String pathToSkin) {
        this.pathToSkin = pathToSkin;
    }

    public PlayerSkin getSkin() {
        if (this.playerSkin != null) {
            return this.playerSkin;
        }

        Path path = Path.of(this.pathToSkin);
        try (InputStream inputStream = Files.newInputStream(path)) {
            NativeImage nativeImage = NativeImage.read(inputStream);

            int w = nativeImage.getWidth();
            int h = nativeImage.getHeight();

            // We determine the type using the alpha of the pixel at 54, 20
            int argb = nativeImage.getPixelRGBA(54 * w / 64, 20 * h / 64);
            String model = "default";
            if (((argb >> 24) & 0xFF) < 20) {
                model = "slim";
            }

            DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);

            ResourceLocation resourceLocation = new ResourceLocation("flashback", "skin_from_file/" + UUID.randomUUID());
            Minecraft.getInstance().getTextureManager().register(resourceLocation, dynamicTexture);
            GlobalCleaner.INSTANCE.register(this, new CleanState(resourceLocation));

            this.playerSkin = new PlayerSkin(resourceLocation, model);
        } catch (Exception e) {
            Flashback.LOGGER.error("Unable to load skin from file", e);
            this.playerSkin = new PlayerSkin(DefaultPlayerSkin.getDefaultSkin(), "default");
        }

        return this.playerSkin;
    }

}
