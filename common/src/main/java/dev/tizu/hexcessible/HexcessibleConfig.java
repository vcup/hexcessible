package dev.tizu.hexcessible;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.EnumHandler.EnumDisplayOption;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry.Translatable;

@Config(name = Hexcessible.MOD_ID)
@Config.Gui.Background("minecraft:textures/block/amethyst_block.png")
public class HexcessibleConfig implements ConfigData {
    private HexcessibleConfig() {
    }

    @ConfigEntry.Gui.Tooltip
    public boolean dimmed = false;
    @ConfigEntry.Gui.Tooltip
    public boolean prefersReducedMotion = false;
    @ConfigEntry.Gui.Tooltip
    public boolean hideFloaties = false;
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.Gui.Excluded
    public boolean showAllDots = false;
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.Gui.EnumHandler(option = EnumDisplayOption.BUTTON)
    public KeyDocs keyDocs = KeyDocs.IDLING;
    /**
     * The key that opens the Hex Book from the casting screen, as a key translation key.
     * <p>
     * Configurable because the default, {@code key.keyboard.n}, collides with Hexical: Hexical binds
     * its own {@code key.hexical.open_hexbook} ("Open Hex Notebook") to N by default and opens the
     * very same book, so with both installed the key is ambiguous. Setting this to
     * {@code key.keyboard.unknown} disables the hotkey entirely, leaving N to Hexical.
     */
    @ConfigEntry.Gui.Tooltip
    public String keyDocsKey = "key.keyboard.n";
    @ConfigEntry.Gui.Tooltip
    public boolean uppercaseSig = false;
    @ConfigEntry.Gui.Tooltip
    public boolean noHexicalWalk = true;
    @ConfigEntry.Gui.Tooltip
    public boolean noHexicalEvoke = true;
    @ConfigEntry.Gui.Tooltip
    public boolean tooltipRenderSigs = true;
    @ConfigEntry.Gui.Tooltip
    public boolean shortcutHints = true;
    /*
     * TODO: @ConfigEntry.Gui.Tooltip
     * public boolean tooltipFixed = false;
     */

    @ConfigEntry.Gui.CollapsibleObject
    public Idle idle = new Idle();
    @ConfigEntry.Gui.CollapsibleObject
    public MouseDraw mouseDraw = new MouseDraw();
    @ConfigEntry.Gui.CollapsibleObject
    public KeyboardDraw keyboardDraw = new KeyboardDraw();
    @ConfigEntry.Gui.CollapsibleObject
    public AutoComplete autoComplete = new AutoComplete();

    @ConfigEntry.Gui.NoTooltip
    public boolean debug = false;

    @ConfigEntry.Gui.Excluded
    /** Bit of a horrible format: <worldctx> <patternid> <sig> */
    public List<String> knownWorldPatterns = List.of();
    @ConfigEntry.Gui.Excluded
    /** <patternid>: <renamed> */
    public Map<String, String> patternAliases = Map.of();

    public static class Idle {
        @ConfigEntry.Gui.EnumHandler(option = EnumDisplayOption.BUTTON)
        public OptionalTooltip tooltip = OptionalTooltip.DESCRIPTIVE;
    }

    public static class MouseDraw {
        @ConfigEntry.Gui.EnumHandler(option = EnumDisplayOption.BUTTON)
        public OptionalTooltip tooltip = OptionalTooltip.DESCRIPTIVE;
    }

    public static class KeyboardDraw {
        @ConfigEntry.Gui.NoTooltip
        public boolean allow = true;
        @ConfigEntry.Gui.EnumHandler(option = EnumDisplayOption.BUTTON)
        public OptionalTooltip tooltip = OptionalTooltip.DESCRIPTIVE;
        @ConfigEntry.Gui.Tooltip
        public boolean keyHint = true;
        @ConfigEntry.Gui.Tooltip
        public boolean ghost = true;
        @ConfigEntry.Gui.Tooltip
        public boolean relative = true;
    }

    public static class AutoComplete {
        @ConfigEntry.Gui.NoTooltip
        public boolean allow = true;
        @ConfigEntry.Gui.EnumHandler(option = EnumDisplayOption.BUTTON)
        public OptionalTooltip tooltip = OptionalTooltip.DESCRIPTIVE;
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 3, max = 20)
        public int count = 7;
    }

    public interface Tooltip extends Translatable {
        boolean visible();

        boolean descriptive();
    }

    public enum ForcedTooltip implements Tooltip {
        SIMPLE, DESCRIPTIVE;

        public boolean descriptive() {
            return this == DESCRIPTIVE;
        }

        public boolean visible() {
            return true;
        }

        @Override
        public @NotNull String getKey() {
            return "text.autoconfig.hexcessible.enum.tooltip." + this.name();
        }
    }

    public enum OptionalTooltip implements Tooltip {
        HIDDEN, SIMPLE, DESCRIPTIVE;

        public boolean descriptive() {
            return this == DESCRIPTIVE;
        }

        public boolean visible() {
            return this != HIDDEN;
        }

        @Override
        public @NotNull String getKey() {
            return "text.autoconfig.hexcessible.enum.tooltip." + this.name();
        }
    }
    
    public enum KeyDocs implements Translatable {
        OFF, IDLING, ALWAYS;

        @Override
        public @NotNull String getKey() {
            return "text.autoconfig.hexcessible.enum.keydocs." + this.name();
        }
    }

    @Override
    public void validatePostLoad() throws ValidationException {
    }

    private static ConfigHolder<HexcessibleConfig> holder;

    static HexcessibleConfig get() {
        var holder = AutoConfig.register(HexcessibleConfig.class, GsonConfigSerializer::new);
        var cfg = AutoConfig.getConfigHolder(HexcessibleConfig.class).getConfig();
        HexcessibleConfig.holder = holder;
        return cfg;
    }

    public void markDirty() {
        Hexcessible.LOGGER.info("Marked config dirty, saving...");
        holder.save();
    }
}
