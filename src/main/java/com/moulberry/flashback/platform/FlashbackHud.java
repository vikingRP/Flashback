package com.moulberry.flashback.platform;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The Forge overlay event is used because ForgeGui does not invoke Gui.render. */
@Mod.EventBusSubscriber(modid = "flashback", value = Dist.CLIENT)
public final class FlashbackHud {
    private FlashbackHud() {}

    public static GameType cameraGameType() {
        Minecraft client = Minecraft.getInstance();
        if (!Flashback.isInReplay() || !(client.getCameraEntity() instanceof Player player)) return null;
        var connection = client.getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(player.getUUID());
        return info == null ? GameType.SPECTATOR : info.getGameMode();
    }

    @SubscribeEvent
    public static void renderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!Flashback.isInReplay()) return;
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.VIGNETTE.id())) {
            event.setCanceled(true);
            return;
        }
        var state = EditorStateManager.getCurrent();
        if (state != null && (ReplayUI.isActive() || Flashback.isExporting())) {
            var visuals = state.replayVisuals;
            boolean hidden = id.equals(VanillaGuiOverlay.CHAT_PANEL.id()) && !visuals.showChat
                || id.equals(VanillaGuiOverlay.TITLE_TEXT.id()) && !visuals.showTitleText
                || id.equals(VanillaGuiOverlay.SCOREBOARD.id()) && !visuals.showScoreboard
                || id.equals(VanillaGuiOverlay.RECORD_OVERLAY.id()) && !visuals.showActionBar;
            if (!visuals.showHotbar) {
                hidden |= id.equals(VanillaGuiOverlay.HOTBAR.id())
                    || id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                    || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
                    || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                    || id.equals(VanillaGuiOverlay.AIR_LEVEL.id())
                    || id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id())
                    || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                    || id.equals(VanillaGuiOverlay.JUMP_BAR.id())
                    || id.equals(VanillaGuiOverlay.ITEM_NAME.id());
            }
            if (hidden) {
                event.setCanceled(true);
                return;
            }
        }
        // Replay clients remain spectators; choose the recorded player's hotbar instead.
        if (id.equals(VanillaGuiOverlay.HOTBAR.id())) {
            Minecraft client = Minecraft.getInstance();
            GameType mode = cameraGameType();
            if (mode != null && mode != GameType.SPECTATOR && !client.options.hideGui) {
                ((ForgeGui) client.gui).setupOverlayRenderState(true, false);
                client.gui.renderHotbar(event.getPartialTick(), event.getGuiGraphics());
                event.setCanceled(true);
            }
        }
    }
}
