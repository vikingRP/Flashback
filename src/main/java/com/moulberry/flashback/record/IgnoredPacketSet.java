package com.moulberry.flashback.record;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.game.*;

import java.util.Set;

public class IgnoredPacketSet {

    public static boolean isIgnored(Packet<?> packet) {
        return IGNORED.contains(packet.getClass());
    }

    public static boolean isIgnoredInReplay(Packet<?> packet) {
        return IGNORED_IN_REPLAY.contains(packet.getClass());
    }

    private static final Set<Class<?>> IGNORED_IN_REPLAY = Set.of(
        ClientboundRecipePacket.class,
        ClientboundAwardStatsPacket.class,



        ClientboundUpdateRecipesPacket.class,

        ClientboundUpdateAdvancementsPacket.class
    );

    private static final Set<Class<?>> IGNORED = Set.of(
        // Ignored because these are added directly by mixin/record/MixinClientLevel
        ClientboundLevelEventPacket.class,
        ClientboundSoundPacket.class,
        ClientboundSoundEntityPacket.class,

        // Common




        ClientboundDisconnectPacket.class,
        ClientboundPingPacket.class,
        ClientboundKeepAlivePacket.class,



        // Configuration


        // Game
        ClientboundRecipePacket.class,
        ClientboundAwardStatsPacket.class,



        ClientboundOpenSignEditorPacket.class,
        ClientboundRotateHeadPacket.class,
        ClientboundMoveEntityPacket.Pos.class,
        ClientboundMoveEntityPacket.Rot.class,
        ClientboundMoveEntityPacket.PosRot.class,
        ClientboundPlayerPositionPacket.class,
        ClientboundPlayerChatPacket.class,
        ClientboundDeleteChatPacket.class,

        ClientboundContainerClosePacket.class,
        ClientboundContainerSetContentPacket.class,
        ClientboundHorseScreenOpenPacket.class,
        ClientboundContainerSetDataPacket.class,
        ClientboundContainerSetSlotPacket.class,
        ClientboundForgetLevelChunkPacket.class,
        ClientboundPlayerAbilitiesPacket.class,

        ClientboundSetExperiencePacket.class,
        ClientboundSetHealthPacket.class,



        ClientboundPlayerCombatEndPacket.class,
        ClientboundPlayerCombatEnterPacket.class,
        ClientboundPlayerCombatKillPacket.class,
        ClientboundSetCameraPacket.class,
        ClientboundCooldownPacket.class,
        ClientboundUpdateAdvancementsPacket.class,
        ClientboundSelectAdvancementsTabPacket.class,
        ClientboundPlaceGhostRecipePacket.class,
        ClientboundCommandsPacket.class,
        ClientboundCommandSuggestionsPacket.class,
        ClientboundUpdateRecipesPacket.class,
        ClientboundTagQueryPacket.class,
        ClientboundOpenBookPacket.class,
        ClientboundOpenScreenPacket.class,
        ClientboundMerchantOffersPacket.class,
        ClientboundSetChunkCacheRadiusPacket.class,
        ClientboundSetSimulationDistancePacket.class,
        ClientboundSetChunkCacheCenterPacket.class,
        ClientboundBlockChangedAckPacket.class,
        ClientboundCustomChatCompletionsPacket.class
    );

}
