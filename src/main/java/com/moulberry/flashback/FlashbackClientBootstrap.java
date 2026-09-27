package com.moulberry.flashback;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ViewportEvent;
import com.moulberry.flashback.visuals.CameraRotation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class FlashbackClientBootstrap {
    private FlashbackClientBootstrap() {}

    static void register() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(FlashbackClientBootstrap::clientSetup);
        bus.addListener(FlashbackClientBootstrap::registerKeyMappings);
        MinecraftForge.EVENT_BUS.addListener(FlashbackClientBootstrap::cameraAngles);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> Flashback.createConfigScreen(parent)));
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> new Flashback().onInitializeClient());
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(Flashback.createMarker1KeyBind);
        event.register(Flashback.createMarker2KeyBind);
        event.register(Flashback.createMarker3KeyBind);
        event.register(Flashback.createMarker4KeyBind);
    }

    private static void cameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!Flashback.isInReplay()) return;
        // Keep other Forge camera handlers' angles and add the editor's roll/shake.
        Quaternionf original = new Quaternionf().rotationYXZ(
            (float)Math.toRadians(-event.getYaw()), (float)Math.toRadians(event.getPitch()),
            (float)Math.toRadians(event.getRoll()));
        Quaternionf modified = CameraRotation.modifyViewQuaternion(original);
        Vector3f angles = modified.getEulerAnglesYXZ(new Vector3f());
        event.setYaw((float)-Math.toDegrees(angles.y));
        event.setPitch((float)Math.toDegrees(angles.x));
        event.setRoll((float)Math.toDegrees(angles.z));
    }
}
