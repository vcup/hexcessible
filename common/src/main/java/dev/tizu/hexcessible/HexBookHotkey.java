package dev.tizu.hexcessible;

import org.jetbrains.annotations.Nullable;

import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import dev.tizu.hexcessible.HexcessibleConfig.KeyDocs;
import dev.tizu.hexcessible.accessor.DrawStateMixinAccessor;
import dev.tizu.hexcessible.drawstate.DrawState;
import dev.tizu.hexcessible.drawstate.Idling;
import dev.tizu.hexcessible.drawstate.KeyboardDrawing;
import dev.tizu.hexcessible.drawstate.MouseDrawing;
import dev.tizu.hexcessible.entries.BookEntries;
import dev.tizu.hexcessible.entries.PatternEntries;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec2f;
import vazkii.patchouli.api.PatchouliAPI;
import vazkii.patchouli.client.book.gui.GuiBook;

/**
 * The "press N for the Hex Book" feature: opens the book from the casting screen, jumping to the
 * hovered pattern's page when there is one, and remembers where to come back to.
 * <p>
 * The book itself is Patchouli's; this only decides when to open it and which page to ask for.
 */
public final class HexBookHotkey {
    private HexBookHotkey() {
    }

    /** The casting screen the book was opened from, or null when the book was not opened by us. */
    @Nullable
    private static GuiSpellcasting staffScreen;
    /** The draw state in progress when the book was opened, restored on return. */
    @Nullable
    private static DrawState resumeAfterBook;
    /** Latest mouse position over the casting screen, used to find the hovered pattern. */
    private static Vec2f mousePos = new Vec2f(0, 0);

    /** Records the cursor position while a casting screen renders. */
    public static void updateMousePos(int mx, int my) {
        mousePos = new Vec2f((float) mx, (float) my);
    }

    /**
     * Handles the configured Hex Book key for the given screen.
     * <p>
     * Called from the same injection that drives the draw-state keys, deliberately: an earlier
     * version had this in its own {@code @Inject} on {@code Screen.keyPressed}, but that injector and
     * the draw-state one both target the method's HEAD, and only one of them actually ran — the
     * book never opened. Sharing one injection removes the ordering question altogether.
     *
     * @return true when the key was consumed.
     */
    public static boolean onKeyPressed(Screen screen, int keyCode) {
        // Every key but the configured one belongs to the draw state, so it must fall through to the
        // caller's normal handling, or the UI would swallow all typing.
        if (!isBoundKey(keyCode))
            return false;
        if (Hexcessible.cfg().keyDocs == KeyDocs.OFF)
            return false;
        if (!(screen instanceof DrawStateMixinAccessor accessor))
            return false;
        var state = accessor.state();
        // ALWAYS deliberately stops short of the two text-entry states: there every character is
        // appended to the query/alias, so the key would open the book while the user is typing it.
        var valid = Hexcessible.cfg().keyDocs == KeyDocs.ALWAYS
                ? state instanceof Idling
                        || state instanceof MouseDrawing
                        || state instanceof KeyboardDrawing
                : state instanceof Idling;
        if (!valid)
            return false;

        staffScreen = (GuiSpellcasting) screen;
        resumeAfterBook = state;
        var hovered = accessor.getPatternAt((int) mousePos.x, (int) mousePos.y);
        if (!openEntry(hovered))
            PatchouliAPI.get().openBookGUI(BookEntries.BOOKID);
        Hexcessible.LOGGER.debug("Opened Hex Book (jumped to hovered entry: {})", hovered != null);
        return true;
    }

    /** Whether the pressed key is the one bound to the Hex Book, per {@code keyDocsKey}. */
    private static boolean isBoundKey(int keyCode) {
        var bound = Hexcessible.cfg().keyDocsKey;
        if (bound == null || bound.isBlank() || bound.equals("key.keyboard.unknown"))
            return false;
        try {
            return InputUtil.fromTranslationKey(bound).getCode() == keyCode;
        } catch (Throwable t) {
            // An unrecognised key name in the config must not disable every key press.
            Hexcessible.LOGGER.warn("Unrecognised keyDocsKey '{}'; hotkey disabled", bound);
            return false;
        }
    }

    private static boolean warnedAboutConflict = false;

    /**
     * Warns once when another mod binds the same key and opens the same book.
     * <p>
     * Hexical binds {@code key.hexical.open_hexbook} ("Open Hex Notebook") to N by default and calls
     * {@code PatchouliAPI.openBookGUI(hexcasting:thehexbook)} — the same key, the same book, and a
     * matching close-redirect. With both installed the key is simply ambiguous: both handlers fire,
     * and the player sees whichever screen ends up on top. That is invisible in a log, so it is
     * reported here rather than left for the user to puzzle out.
     * <p>
     * Detection is by keybinding rather than by mod id, so it also catches any future mod that does
     * the same thing. Called from the casting screen's init.
     */
    public static void warnIfKeyConflicts() {
        if (warnedAboutConflict)
            return;
        warnedAboutConflict = true;
        var bound = Hexcessible.cfg().keyDocsKey;
        if (bound == null || bound.isBlank() || bound.equals("key.keyboard.unknown"))
            return;
        try {
            for (var other : MinecraftClient.getInstance().options.allKeys) {
                if (other == null)
                    continue;
                var id = other.getTranslationKey();
                if (!"key.hexical.open_hexbook".equals(id))
                    continue;
                if (other.isUnbound() || !other.getBoundKeyTranslationKey().equals(bound))
                    continue;
                Hexcessible.LOGGER.warn(
                        "keyDocsKey '{}' is also bound by '{}', which opens the same book; change "
                                + "text.autoconfig.hexcessible.option.keyDocsKey to avoid the clash",
                        bound, id);
                return;
            }
        } catch (Throwable t) {
            // A conflict check must never break the casting screen.
            Hexcessible.LOGGER.debug("Key conflict check skipped", t);
        }
    }

    /** Opens the book at the hovered pattern's page, or returns false if that cannot be resolved. */
    private static boolean openEntry(@Nullable HexPattern hovered) {
        if (hovered == null)
            return false;
        var pattern = PatternEntries.INSTANCE.getFromSig(hovered.getAngles());
        if (pattern == null)
            return false;
        var entry = BookEntries.INSTANCE.getBookEntryFor(pattern.id().toString());
        if (entry == null || entry.entryid() == null)
            return false;
        PatchouliAPI.get().openBookEntry(BookEntries.BOOKID, entry.entryid(), entry.page());
        return true;
    }

    /**
     * Sends the user back to the casting screen when a book we opened is closed.
     * <p>
     * {@code setScreen} re-runs {@code init} on the casting screen, which would rebuild the draw
     * state and drop whatever the user had half-drawn, so the state is handed over first — inside
     * {@code setScreen}, because {@code init} runs synchronously there.
     *
     * @return true when the close was redirected.
     */
    public static boolean returnToStaff(Screen screen) {
        if (!(screen instanceof GuiBook) || staffScreen == null)
            return false;
        var staffAccessor = (DrawStateMixinAccessor) (Object) staffScreen;
        staffAccessor.hexcessible$resumeState(resumeAfterBook);
        MinecraftClient.getInstance().setScreen(staffScreen);
        staffScreen = null;
        resumeAfterBook = null;
        return true;
    }

    /** True when this screen is a book we opened from the casting screen. */
    public static boolean isOurBook(Screen screen) {
        return screen instanceof GuiBook && staffScreen != null;
    }
}
