package com.moulberry.flashback.mixin.ui;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.screen.FlashbackButton;
import com.moulberry.flashback.screen.select_replay.SelectReplayScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Mirrors the title screen button so replays stay reachable when another mod replaces the main menu
@Mixin(OptionsScreen.class)
public abstract class MixinOptionsScreen extends Screen {

    protected MixinOptionsScreen(Component component) {
        super(component);
    }

    @Inject(method = "init", at = @At("RETURN"))
    public void init(CallbackInfo ci) {
        // Opening a replay tears down the current world, so only offer it from outside a world
        if (this.minecraft == null || this.minecraft.level != null) {
            return;
        }

        int size = 20;
        int x = this.width - size - 4;
        int y = 4;

        // Place the button next to the "Done" button if we can find it
        for (Renderable renderable : this.renderables) {
            if (renderable instanceof AbstractWidget widget && widget.getMessage().equals(CommonComponents.GUI_DONE)) {
                x = widget.getX() + widget.getWidth() + 4;
                y = widget.getY();
                break;
            }
        }

        this.addRenderableWidget(new FlashbackButton(x, y, size, size, Component.translatable("flashback.open_replays"), button -> {
            List<String> incompatibleMods = Screen.hasShiftDown() ? List.of() : Flashback.getReplayIncompatibleMods();

            if (incompatibleMods.isEmpty()) {
                this.minecraft.setScreen(new SelectReplayScreen(this));
            } else {
                String mods = StringUtils.join(incompatibleMods, ", ");
                Component description = Component.translatable("flashback.incompatible_with_viewing_description").append(Component.literal(mods).withStyle(ChatFormatting.RED));
                this.minecraft.setScreen(new AlertScreen(() -> Minecraft.getInstance().setScreen(this),
                    Component.translatable("flashback.incompatible_with_viewing"), description));
            }
        }).flashbackWithTooltip());
    }

}
