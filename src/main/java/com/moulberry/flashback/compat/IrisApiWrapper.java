package com.moulberry.flashback.compat;

import com.moulberry.flashback.platform.ForgePlatform;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import org.jetbrains.annotations.Nullable;

public class IrisApiWrapper {

    public static boolean isIrisAvailable() {
        return ForgePlatform.getInstance().isModLoaded("iris") || ForgePlatform.getInstance().isModLoaded("oculus");
    }

    public static boolean isShaderPackInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

}
