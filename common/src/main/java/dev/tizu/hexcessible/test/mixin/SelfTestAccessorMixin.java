package dev.tizu.hexcessible.test.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import dev.tizu.hexcessible.test.SelfTestAccess;

/**
 * Exposes the casting UI's resolved-pattern list to the self-test driver. Self-test build only.
 */
@Mixin(GuiSpellcasting.class)
public abstract class SelfTestAccessorMixin implements SelfTestAccess {

    @Shadow(remap = false)
    private List<ResolvedPattern> patterns;

    @Override
    public List<ResolvedPattern> hexcessible$selftestPatterns() {
        return patterns;
    }
}
