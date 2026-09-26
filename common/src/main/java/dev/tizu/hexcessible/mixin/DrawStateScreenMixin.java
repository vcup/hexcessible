package dev.tizu.hexcessible.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.tizu.hexcessible.HexBookHotkey;
import dev.tizu.hexcessible.accessor.DrawStateMixinAccessor;
import net.minecraft.client.gui.screen.Screen;

/**
 * Routes key presses on a casting screen into the hexcessible draw state.
 * <p>
 * This is the only handler on {@code Screen.keyPressed}: the Hex Book hotkey is dispatched from here
 * too (via {@link HexBookHotkey}) rather than from an injection of its own, because two injectors at the
 * same method and point are not both guaranteed to run — and in practice only one did, which left
 * the book hotkey dead.
 */
@Mixin(Screen.class)
public class DrawStateScreenMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof DrawStateMixinAccessor accessor))
            return;
        // The book hotkey is checked first so it can act while a draw is in progress (ALWAYS).
        if (keyCode != GLFW.GLFW_KEY_ESCAPE && HexBookHotkey.onKeyPressed((Screen) (Object) this, keyCode)) {
            cir.setReturnValue(true);
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE)
            accessor.state().requestExit();
        else
            accessor.state().onKeyPress(keyCode, modifiers);
        cir.setReturnValue(true);
    }
}
