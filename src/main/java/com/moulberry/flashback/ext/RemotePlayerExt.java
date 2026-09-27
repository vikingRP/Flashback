package com.moulberry.flashback.ext;
import net.minecraft.world.item.ItemStack;
public interface RemotePlayerExt {
    float flashback$getXBob(float tick);
    float flashback$getYBob(float tick);
    ItemStack flashback$getMainHand();
    ItemStack flashback$getOffHand();
    float flashback$getMainHandHeight(float tick);
    float flashback$getOffHandHeight(float tick);
}
