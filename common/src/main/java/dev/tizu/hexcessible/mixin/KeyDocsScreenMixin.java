package dev.tizu.hexcessible.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.tizu.hexcessible.HexBookHotkey;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/**
 * Hooks the Hex Book hotkey and the return trip into {@code Screen}.
 * <p>
 * Pressing N is handled by {@code DrawStateScreenMixin}'s {@code keyPressed} injection, not by one of
 * its own here. Two injectors on the same method and point do not both run: the draw-state handler
 * consumes the key (it reports the key as handled), so a second HEAD injector with a lower order
 * never executes. That is exactly what happened before — the book never opened at all. This mixin
 * therefore covers only what has no sibling handler: cursor tracking and the close.
 */
@Mixin(Screen.class)
public class KeyDocsScreenMixin {

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    void returnToStaff(CallbackInfo ci) {
        if (HexBookHotkey.returnToStaff((Screen) (Object) this))
            ci.cancel();
    }

    @Inject(method = "render", at = @At("HEAD"))
    void render(DrawContext ctx, int mx, int my, float delta, CallbackInfo info) {
        if ((Object) this instanceof dev.tizu.hexcessible.accessor.DrawStateMixinAccessor)
            HexBookHotkey.updateMousePos(mx, my);
    }
}
