package dev.tizu.hexcessible.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.tizu.hexcessible.DrawStateInput;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;

/**
 * Routes typed characters into the hexcessible draw state while a casting UI is on screen.
 * <p>
 * This targets {@code Keyboard.onChar}, the single point where GLFW character input becomes
 * {@code Element.charTyped} calls. Only the casting screen is intercepted; every other screen keeps
 * its vanilla dispatch to the focused element untouched.
 * <p>
 * The natural-looking target is {@code ParentElement.charTyped}, the screen-level method, but that is
 * an <em>interface default method</em>, and upstream Mixin refuses to inject into one ("is not
 * supported on interface mixin method handler"). Fabric's Mixin fork — the one Sinytra Connector
 * bundles as {@code fabric-mixin.jar} — does allow it, which is why this worked while the mod was a
 * Fabric jar running through Connector; NeoForge uses upstream Mixin 0.8.5, where it fails hard
 * during class load. Hooking the concrete call site behaves identically on both loaders.
 */
@Mixin(Keyboard.class)
public class DrawStateCharTypedMixin {

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void hexcessible$onChar(long window, int codepoint, int modifiers, CallbackInfo info) {
        var client = MinecraftClient.getInstance();
        // The same guards vanilla applies before dispatching: the right window, and no overlay.
        if (window != client.getWindow().getHandle())
            return;
        if (client.getOverlay() != null)
            return;
        if (DrawStateInput.typeChar(client, codepoint))
            info.cancel();
    }
}
