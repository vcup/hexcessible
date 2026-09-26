package dev.tizu.hexcessible.test.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.tizu.hexcessible.test.HexcessibleSelfTest;
import net.minecraft.client.MinecraftClient;

/**
 * Ticks the in-game self-test driver once per client tick.
 * <p>
 * Only present in a {@code -Pselftest} build: the whole {@code test} package is excluded from a
 * release jar, and {@code hexcessible.selftest.mixins.json} (the config that declares this mixin) is
 * excluded alongside it and never registered by the platform metadata.
 * <p>
 * This lives in {@code test.mixin} rather than {@code test} because Mixin forbids referencing a
 * class that sits inside a declared mixin package from outside the transformer — the driver itself
 * ({@code HexcessibleSelfTest}) has to be a normal class.
 */
@Mixin(MinecraftClient.class)
public class SelfTestTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void hexcessible$selftestTick(CallbackInfo info) {
        HexcessibleSelfTest.onClientTick();
    }
}
