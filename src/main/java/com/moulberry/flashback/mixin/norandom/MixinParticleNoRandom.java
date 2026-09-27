package com.moulberry.flashback.mixin.norandom;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Random;

@Mixin(Particle.class)
public class MixinParticleNoRandom {
    // Seed the field initializer so lifetime and subclass initialization use the same RNG.
    @WrapOperation(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDD)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;create()Lnet/minecraft/util/RandomSource;"))
    private static RandomSource flashback$seedParticleRandom(Operation<RandomSource> original) {
        if (Flashback.isExporting()) {
            Random random = Flashback.EXPORT_JOB.getParticleRandom();
            if (random != null) return RandomSource.create(random.nextLong());
        }
        return original.call();
    }

    // 1.20.1 uses Math.random for the initial particle velocity as well.
    @WrapOperation(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)V",
        at = @At(value = "INVOKE", target = "Ljava/lang/Math;random()D"))
    private static double flashback$seedParticleVelocity(Operation<Double> original) {
        if (Flashback.isExporting()) {
            Random random = Flashback.EXPORT_JOB.getParticleRandom();
            if (random != null) return random.nextDouble();
        }
        return original.call();
    }
}
