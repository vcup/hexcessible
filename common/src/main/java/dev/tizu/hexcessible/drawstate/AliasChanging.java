package dev.tizu.hexcessible.drawstate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.lwjgl.glfw.GLFW;

import dev.tizu.hexcessible.Hexcessible;
import dev.tizu.hexcessible.accessor.CastRef;
import dev.tizu.hexcessible.entries.PatternEntries;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class AliasChanging extends DrawState {
    private String alias;
    private final String original;
    private final String sig;
    private final String id;

    public AliasChanging(CastRef castref, PatternEntries.Entry entry) {
        super(castref);
        this.alias = entry.isAliased() ? entry.name() : "";
        this.original = entry.rawName();
        this.sig = entry.toSignature();
        this.id = entry.id();
    }

    @Override
    public void onRender(DrawContext ctx, int mx, int my) {
        var tr = MinecraftClient.getInstance().textRenderer;

        var x = ctx.getScaledWindowWidth() / 3;
        var y = ctx.getScaledWindowHeight() / 2;

        var originalStr = sig + " " + original;
        var originalT = alias.isBlank()
                ? Text.literal(originalStr).formatted(Formatting.BLUE)
                : Text.literal(originalStr).formatted(Formatting.GRAY);
        ctx.drawTooltip(tr, originalT, x, y - 1);

        var aliasT = alias.isBlank()
                ? Text.translatable("hexcessible.start_typing.alias")
                        .formatted(Formatting.DARK_GRAY)
                : Text.literal(alias)
                        .formatted(Formatting.BLUE);
        ctx.drawTooltip(tr, aliasT, x, y + 16);
    }

    @Override
    public void onCharType(char chr) {
        alias = alias + chr;
    }

    @Override
    public void onKeyPress(int keyCode, int modifiers) {
        var ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (ctrl) { // remove last word
                    var words = alias.split(" ");
                    alias = Arrays.stream(words)
                            .limit(words.length - 1l)
                            .collect(Collectors.joining(" "));
                } else { // remove single character
                    alias = alias.isEmpty() ? ""
                            : alias.substring(0, alias.length() - 1);
                }
                break;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB:
                var map = new HashMap<>(Hexcessible.cfg().patternAliases);
                map.put(id, alias.isBlank() ? original : alias.trim());
                Hexcessible.cfg().patternAliases = map;
                Hexcessible.cfg().markDirty();
                requestExit();
                break;
            default:
        }
    }

    @Override
    public Map<String, String> getHints() {
        var keys = new HashMap<String, String>();

        keys.put("tab/enter", alias.isBlank() ? "alias_off" : "alias");

        return keys;
    }
}