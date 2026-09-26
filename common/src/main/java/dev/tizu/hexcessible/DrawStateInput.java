package dev.tizu.hexcessible;

import dev.tizu.hexcessible.accessor.DrawStateMixinAccessor;
import net.minecraft.client.MinecraftClient;

/**
 * Routes keyboard character input to the casting UI's draw state.
 * <p>
 * This lives outside the mixin so the routing rule can be called directly (by the in-game self-test)
 * as well as by the input hook, and so the hook itself stays a two-line adapter. Character input
 * reaches a screen through {@code Keyboard.onChar}, which is where the mod intercepts — see
 * {@code DrawStateCharTypedMixin} for why the screen-level method cannot be used.
 */
public final class DrawStateInput {
    private DrawStateInput() {
    }

    /**
     * Offers a typed codepoint to the casting UI when one is on screen.
     *
     * @return true when the character was consumed and vanilla dispatch should be skipped.
     */
    public static boolean typeChar(MinecraftClient client, int codepoint) {
        if (!(client.currentScreen instanceof DrawStateMixinAccessor accessor))
            return false;
        // Mirrors vanilla: a single-code-unit codepoint goes straight through, while one needing a
        // surrogate pair is handed over character by character.
        if (Character.charCount(codepoint) == 1) {
            accessor.state().onCharType((char) codepoint);
        } else {
            for (var chr : Character.toChars(codepoint))
                accessor.state().onCharType(chr);
        }
        return true;
    }
}
