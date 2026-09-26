package dev.tizu.hexcessible.mixin;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import dev.tizu.hexcessible.Hexcessible;
import dev.tizu.hexcessible.accessor.CastRef;
import dev.tizu.hexcessible.accessor.CastingInterfaceAccessor;
import dev.tizu.hexcessible.accessor.DrawStateMixinAccessor;
import dev.tizu.hexcessible.drawstate.DrawState;
import dev.tizu.hexcessible.entries.PatternEntries;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec2f;

@Mixin(GuiSpellcasting.class)
public abstract class DrawStateMixin implements DrawStateMixinAccessor {
    @Unique
    private CastRef castref;
    @Unique
    private DrawState state;
    @Unique
    private boolean noActing;

    @Shadow(remap = false)
    private Hand handOpenedWith;
    @Shadow(remap = false)
    private List<ResolvedPattern> patterns;
    @Shadow(remap = false)
    private Set<HexCoord> usedSpots;

    /**
     * Hex Casting's own {@code drawEnd}, which commits the pattern currently being drawn.
     * <p>
     * A shadow contributes no body to the target — it is only a handle on the private method, hence
     * the abstract declaration (and the abstract mixin class).
     */
    @Shadow(remap = false)
    protected abstract boolean drawEnd();

    /**
     * The accessor interface's {@code hexcessible$drawEnd}, which {@link CastRef} calls through
     * {@code stopDrawing()} whenever {@code AutoCompleting} stops the in-progress pattern.
     * <p>
     * This has to be a separate, concrete member. When the accessor name was itself declared as the
     * shadow (via {@code @Shadow(prefix = "hexcessible$")}), nothing implemented the interface
     * member: the shadow only renames the target's method, so the interface method stayed abstract
     * and every call died with {@code AbstractMethodError: GuiSpellcasting.hexcessible$drawEnd()Z is
     * abstract} — which is exactly what typing in the autocomplete box triggered.
     */
    @Override
    public boolean hexcessible$drawEnd() {
        return this.drawEnd();
    }

    @Inject(at = @At("HEAD"), method = "init")
    private void init(CallbackInfo info) {
        PatternEntries.INSTANCE.invalidateCaches();
        var castui = (GuiSpellcasting) (Object) this;
        var accessor = new CastingInterfaceAccessor(castui);
        castref = new CastRef(castui, accessor, handOpenedWith, patterns,
                usedSpots, this::hexcessible$drawEnd);
        state = DrawState.getNew(castref);
        noActing = !(MinecraftClient.getInstance().currentScreen instanceof GuiSpellcasting);
    }

    @Inject(at = @At("HEAD"), method = "mouseMoved")
    private void mouseMoved(double mx, double my, CallbackInfo info) {
        state.onMouseMove(mx, my);
    }

    @Inject(at = @At("HEAD"), method = "mouseClicked")
    private void mouseClicked(double mx, double my, int button, CallbackInfoReturnable<Boolean> info) {
        state.onMousePress(mx, my, button);
    }

    @Inject(at = @At("HEAD"), method = "mouseScrolled", cancellable = true)
    private void mouseScrolled(double mx, double my, double delta, CallbackInfoReturnable<Boolean> info) {
        if (state.onMouseScroll((int) delta))
            info.setReturnValue(true);
    }

    @Inject(at = @At("RETURN"), method = "render")
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta,
            CallbackInfo info) {
        if (!noActing && DrawState.shouldClose(state)) {
            ((GuiSpellcasting) (Object) this).close();
            return;
        }

        var nextState = DrawState.updateRequired((GuiSpellcasting) (Object) this, state);
        if (nextState != null)
            state = nextState;

        if (Hexcessible.cfg().debug) {
            renderDebug(ctx, state.getClass().getSimpleName(), 0);
            var debug = state.getDebugInfo();
            for (int i = 0; i < debug.size(); i++)
                renderDebug(ctx, debug.get(i), i + 1);
        }

        if (!noActing) {
            state.onRender(ctx, mouseX, mouseY);
            renderHints(ctx);
        }
    }

    @Unique
    private void renderDebug(DrawContext ctx, String text, int i) {
        ctx.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                text, 5, 5 + (i * 10), 0xFFFFFF);
    }

    @Unique
    private void renderHints(DrawContext ctx) {
        if (!Hexcessible.cfg().shortcutHints)
            return;
        var hints = state.getHints();
        if (hints.isEmpty())
            return;

        var x = 6;
        var y = ctx.getScaledWindowHeight() - 16;
        for (var hint : hints.entrySet()) {
            var text = Text.empty()
                    .append(Text.literal(hint.getKey() + " ")
                            .formatted(Formatting.WHITE))
                    .append(Text.translatable("hexcessible.hint." + hint.getValue())
                            .formatted(Formatting.GRAY));
            ctx.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    text, x, y, 0xFFFFFF);
            y -= 10;
        }
    }

    @WrapMethod(method = "drawStart", remap = false)
    private boolean drawStart(double mxOut, double myOut, Operation<Boolean> original) {
        if (!state.allowStartDrawing())
            return false;
        return original.call(mxOut, myOut);
    }

    @Override
    public DrawState state() {
        return state;
    }

    @Override
    public @Nullable HexPattern getPatternAt(int x, int y) {
        var coord = ((GuiSpellcasting) (Object) this).pxToCoord(new Vec2f(x, y));
        return patterns.stream()
                .filter(p -> p.getOrigin().equals(coord)
                        || p.getPattern().positions().stream()
                                .map(pt -> pt.plus(p.getOrigin()))
                                .anyMatch(pt -> pt.equals(coord)))
                .findFirst()
                .map(ResolvedPattern::getPattern)
                .orElse(null);
    }

    @Override
    public void disallowTyping() {
        castref.disallowTyping();
    }
}
