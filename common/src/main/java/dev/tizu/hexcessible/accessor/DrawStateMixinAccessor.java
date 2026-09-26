package dev.tizu.hexcessible.accessor;

import org.jetbrains.annotations.Nullable;

import at.petrak.hexcasting.api.casting.math.HexPattern;
import dev.tizu.hexcessible.drawstate.DrawState;

public interface DrawStateMixinAccessor {
    DrawState state();

    @Nullable
    HexPattern getPatternAt(int x, int y);

    void disallowTyping();

    boolean hexcessible$drawEnd();

    /**
     * Restores a draw state across a screen re-initialisation.
     * <p>
     * Returning from the Hex Book goes through {@code MinecraftClient.setScreen}, which calls
     * {@code init} on the casting screen again; without this the in-progress draw state would be
     * discarded and the user's half-typed pattern lost. Must be called immediately <em>before</em>
     * {@code setScreen}, because {@code init} runs synchronously inside it.
     */
    void hexcessible$resumeState(DrawState previous);
}