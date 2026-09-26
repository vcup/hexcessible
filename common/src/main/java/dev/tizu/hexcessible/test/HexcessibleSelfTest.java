package dev.tizu.hexcessible.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Queue;

import org.lwjgl.glfw.GLFW;

import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import dev.tizu.hexcessible.DrawStateInput;
import dev.tizu.hexcessible.Hexcessible;
import dev.tizu.hexcessible.Utils;
import dev.tizu.hexcessible.accessor.DrawStateMixinAccessor;
import dev.tizu.hexcessible.entries.BookEntries;
import dev.tizu.hexcessible.entries.PatternEntries;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;

/**
 * Hexcessible in-game self-test driver (client side; self-test builds only).
 * <p>
 * The mod's behaviour lives in client screens and mixins, which a compile check or a dedicated
 * server cannot reach. This driver therefore runs inside the real client and scripts the UI through
 * the game's own input methods — {@code Screen.mouseMoved}/{@code mouseClicked}/{@code mouseDragged},
 * {@code charTyped}, {@code keyPressed} — exactly as Minecraft's input loop calls them. Nothing is
 * sent to the host: no cursor movement, no synthetic keystrokes, no window focus manipulation.
 * <p>
 * Commands are read from {@code hexcessible-selftest.txt} in the game directory, one per line and
 * one per client tick, so timing is explicit. Results are logged with a {@code HEXTEST} prefix and a
 * final {@code HEXTEST SUMMARY}, so a run is asserted by grepping the client log.
 */
public final class HexcessibleSelfTest {
    private HexcessibleSelfTest() {
    }

    private static final String PREFIX = "HEXTEST ";
    private static final Queue<String> QUEUE = new ArrayDeque<>();
    /**
     * Commands to run before {@link #QUEUE}. A pending "wait N" or "waitjoin N" belongs here: it
     * must hold up the commands that follow it, so it cannot simply be appended to the back of the
     * main queue (which would let everything else run first and defeat the wait entirely).
     */
    private static final Deque<String> PENDING = new ArrayDeque<>();
    private static boolean started = false;
    private static boolean done = false;
    private static int steps = 0;
    private static int failures = 0;

    private static void log(String msg) {
        Hexcessible.LOGGER.info("{}{}", PREFIX, msg);
    }

    private static void ok(String what) {
        log("PASS " + what);
    }

    private static void fail(String what, String why) {
        failures++;
        log("FAIL " + what + " :: " + why);
    }

    private static void check(boolean cond, String what, String why) {
        if (cond)
            ok(what);
        else
            fail(what, why);
    }

    /**
     * Called once per client tick from {@code SelfTestTickMixin}.
     * <p>
     * Runs from the moment the client is up, not just once a world is loaded: the script's first
     * job is usually to join a server, which cannot wait for a world that only exists afterwards.
     * Commands that need a live world check for one themselves.
     */
    public static void onClientTick() {
        var mc = MinecraftClient.getInstance();
        if (mc == null)
            return;
        if (!started) {
            started = true;
            loadScript(mc);
            return;
        }
        if (done)
            return;
        // Anything scheduled to run first (a wait, or a world-readiness poll) takes precedence.
        String line;
        if (!PENDING.isEmpty()) {
            line = PENDING.pollFirst();
        } else if (!QUEUE.isEmpty()) {
            line = QUEUE.poll();
        } else {
            done = true;
            log("SUMMARY failures=" + failures + " steps=" + steps);
            return;
        }
        steps++;
        try {
            run(mc, line);
        } catch (Throwable t) {
            fail(line, t.getClass().getSimpleName() + ": " + t.getMessage());
            Hexcessible.LOGGER.error("HEXTEST exception on: {}", line, t);
        }
    }

    /** True once a world is loaded and commands that touch gameplay can run. */
    private static boolean inWorld() {
        var mc = MinecraftClient.getInstance();
        return mc.player != null && mc.world != null;
    }

    private static void loadScript(MinecraftClient mc) {
        var path = mc.runDirectory.toPath().resolve("hexcessible-selftest.txt");
        if (!Files.exists(path)) {
            done = true;
            return; // inert unless a script is present
        }
        try {
            for (var raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                // Strip a UTF-8 BOM: an editor that adds one puts it in front of the first command,
                // which then reads as an unknown command instead of whatever it was meant to be.
                var l = raw.replace("\uFEFF", "").trim();
                if (!l.isEmpty() && !l.startsWith("#"))
                    QUEUE.add(l);
            }
            log("loaded " + QUEUE.size() + " script lines");
        } catch (IOException e) {
            fail("loadScript", e.toString());
        }
    }

    // ------------------------------------------------------------------ script

    private static void run(MinecraftClient mc, String line) throws Throwable {
        var sp = line.split("\\s+", 2);
        var cmd = sp[0];
        var arg = sp.length > 1 ? sp[1] : "";
        switch (cmd) {
            case "nop" -> {
                // consumes exactly one tick
            }
            case "wait" -> {
                // Re-queue at the FRONT so the rest of the script is held back for N ticks.
                var n = Integer.parseInt(arg);
                for (var i = 1; i < n; i++)
                    PENDING.addLast("nop");
            }
            case "cmd" -> cmd_(mc, "/" + arg);
            case "join" -> join(mc, arg);
            case "waitjoin" -> waitjoin(mc, Integer.parseInt(arg));
            case "wantscreen" -> wantscreen(mc, arg);
            case "usestaff" -> usestaff(mc);
            case "state" -> assertState(arg);
            case "screen" -> assertScreen(arg);
            case "move" -> move(mc, arg);
            case "click" -> click(mc, arg);
            case "scroll" -> scroll(mc, arg);
            case "hexdrag" -> hexdrag(mc, arg);
            case "chars" -> chars(mc, arg);
            case "key" -> key(mc, arg);
            case "hints" -> hints();
            case "dumpstate" -> dumpState();
            case "close" -> {
                var s = mc.currentScreen;
                if (s != null)
                    s.close();
                ok("close");
            }
            case "entries" -> entries(arg);
            case "perworld" -> perworld();
            case "books" -> books(arg);
            case "cfg" -> cfg(arg);
            case "cast" -> cast(mc, arg);
            case "haspacket" -> haspacket(arg);
            case "configscreen" -> configScreen();
            case "mixins" -> mixins();
            case "selectslot" -> selectslot(mc, arg);
            case "held" -> held();
            case "screenshot" -> screenshot(mc, arg);
            case "lang" -> lang();
            case "note" -> log("NOTE " + arg);
            case "keybinds" -> keybinds(arg);
            default -> fail(line, "unknown command");
        }
    }

    /**
     * Lists registered key bindings, filtered by a substring of their translation key.
     * <p>
     * Used to check whether another mod's key binding is discoverable through the vanilla options at
     * all: mods that register through a loader-specific helper may not appear in
     * {@code GameOptions.allKeys}, which would make a key-binding-based conflict check useless.
     */
    private static void keybinds(String filter) {
        var options = MinecraftClient.getInstance().options;
        var matching = new java.util.ArrayList<String>();
        for (var kb : options.allKeys) {
            if (kb == null)
                continue;
            var id = kb.getTranslationKey();
            if (filter.isEmpty() || id.contains(filter))
                matching.add(id + "=" + (kb.isUnbound() ? "unbound" : kb.getBoundKeyTranslationKey()));
        }
        log("KEYBINDS total=" + options.allKeys.length + " filter='" + filter + "' -> " + matching);
    }

    private static void cmd_(MinecraftClient mc, String msg) {
        var net = mc.getNetworkHandler();
        if (net == null) {
            fail("cmd " + msg, "no network handler");
            return;
        }
        if (msg.startsWith("/"))
            net.sendCommand(msg.substring(1));
        else
            net.sendChatMessage(msg);
        ok("cmd " + msg);
    }

    /**
     * Joins a server explicitly, rather than relying on {@code --quickPlayMultiplayer}.
     * <p>
     * Quick play is skipped by the client whenever it cannot reach Mojang's session services (it
     * waits on a profile key pair first), which makes an offline test run hang at the title screen.
     * Driving {@code ConnectScreen.connect} here has no such dependency.
     */
    private static void join(MinecraftClient mc, String address) {
        try {
            var parsed = ServerAddress.parse(address);
            ConnectScreen.connect(mc.currentScreen, mc, parsed,
                    new ServerInfo("hexisle-selftest", address, false), false);
            ok("join " + address);
        } catch (Throwable t) {
            fail("join " + address, t.toString());
        }
    }

    /**
     * Waits up to N ticks for a world to load, polling rather than blocking: the script advances one
     * line per tick, so this re-queues itself at the FRONT of the pending queue, which holds back
     * every command that follows it until the world actually exists.
     */
    private static void waitjoin(MinecraftClient mc, int ticks) {
        if (inWorld()) {
            ok("waitjoin (world loaded)");
            return;
        }
        if (ticks <= 0) {
            fail("waitjoin", "world never loaded (screen=" + screenName() + ")");
            return;
        }
        PENDING.addFirst("waitjoin " + (ticks - 1));
    }

    /**
     * Uses the held item through the vanilla interaction path, which is how the cast UI opens.
     * <p>
     * Asserts what is actually held first. Hex Casting opens nothing for an empty hand, so without
     * this the command reported success while the client sat at the world with no UI open, and the
     * whole script that followed failed for a reason the log never named.
     */
    private static void usestaff(MinecraftClient mc) {
        var im = mc.interactionManager;
        if (im == null) {
            fail("usestaff", "no interaction manager");
            return;
        }
        var held = mc.player.getMainHandStack();
        if (held.isEmpty()) {
            fail("usestaff", "main hand is empty (selected slot "
                    + mc.player.getInventory().selectedSlot + ")");
            return;
        }
        im.interactItem(mc.player, Hand.MAIN_HAND);
        ok("usestaff with " + held.getItem());
    }

    /**
     * Selects the hotbar slot holding the first item whose id contains {@code needle}.
     * <p>
     * A fixed slot index is not safe: {@code /give} lands in the first free slot, which depends on
     * what the player is already carrying, and the Fabric run had a guide book in slot 0 and the
     * staff in slot 1 — so selecting "slot 0" held the wrong item and opening the Hex Book flooded
     * the whole run with "no casting UI" failures that said nothing about their real cause.
     */
    private static void selectslot(MinecraftClient mc, String needle) {
        var inv = mc.player.getInventory();
        for (var i = 0; i < 9; i++) {
            var st = inv.getStack(i);
            if (!st.isEmpty() && net.minecraft.registry.Registries.ITEM
                    .getId(st.getItem()).toString().contains(needle)) {
                inv.selectedSlot = i;
                ok("selectslot " + needle + " -> slot " + i + " (" + inv.getMainHandStack().getItem() + ")");
                return;
            }
        }
        fail("selectslot " + needle,
                "not in the hotbar; held=" + inv.getMainHandStack().getItem());
    }

    /**
     * Reports what the player is holding, which is what decides whether a staff actually casts.
     */
    private static void held() {
        var mc = MinecraftClient.getInstance();
        var inv = mc.player.getInventory();
        var sb = new StringBuilder();
        for (var i = 0; i < 9; i++) {
            var st = inv.getStack(i);
            if (!st.isEmpty())
                sb.append(i).append('=').append(st.getItem()).append(' ');
        }
        log("HELD selected=" + inv.selectedSlot + " main=" + inv.getMainHandStack().getItem()
                + " off=" + mc.player.getOffHandStack().getItem() + " hotbar[" + sb.toString().trim() + "]");
    }

    /**
     * Writes a PNG of the current frame, so the run can be checked against what was on screen
     * instead of inferred from log lines.
     */
    private static void screenshot(MinecraftClient mc, String name) {
        try {
            var dir = new java.io.File(mc.runDirectory, "screenshots");
            dir.mkdirs();
            var file = new java.io.File(dir, name + ".png");
            net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(
                    mc.runDirectory, name + ".png", mc.getFramebuffer(), msg -> { });
            ok("screenshot " + file.getName());
        } catch (Throwable t) {
            fail("screenshot " + name, t.toString());
        }
    }

    /**
     * Asserts the mod's own translations actually resolve.
     * <p>
     * {@code Text.translatable} silently falls back to the raw key when the key is unknown, which is
     * how every hint in the casting UI rendered as "hexcessible.hint.cast" on screen: the lang file
     * was in the jar, but the jar carried no {@code pack.mcmeta}, so Forge never registered it as a
     * resource pack and none of our assets were loaded. Fabric Loader registers mod jars
     * automatically, so this only breaks on the NeoForge side — hence an explicit check.
     */
    private static void lang() {
        var keys = List.of(
                "hexcessible.hint.draw_start", "hexcessible.hint.auto_complete",
                "hexcessible.hint.cast", "hexcessible.hint.move",
                "hexcessible.hint.rotate", "hexcessible.hint.undo",
                "hexcessible.start_typing", "hexcessible.no_space");
        var missing = new java.util.ArrayList<String>();
        for (var k : keys) {
            var resolved = net.minecraft.text.Text.translatable(k).getString();
            if (resolved.equals(k))
                missing.add(k);
        }
        check(missing.isEmpty(), "translations resolve (" + keys.size() + " keys)",
                "unresolved (raw key shown): " + missing);
    }

    // -------------------------------------------------------------- assertions

    private static DrawStateMixinAccessor accessor() {
        var s = MinecraftClient.getInstance().currentScreen;
        return s instanceof DrawStateMixinAccessor a ? a : null;
    }

    private static void assertState(String expected) {
        var a = accessor();
        if (a == null) {
            fail("state " + expected, "no casting UI (screen=" + screenName() + ")");
            return;
        }
        var actual = a.state().getClass().getSimpleName();
        check(actual.equals(expected), "state " + expected, "was " + actual);
    }

    private static void assertScreen(String expected) {
        var actual = screenName();
        check(actual.equals(expected), "screen " + expected, "was " + actual);
    }

    /**
     * Polls until a screen of the given type is open, up to a tick budget.
     * <p>
     * Screen changes are not synchronous with the input that causes them: opening the Hex Book goes
     * through Patchouli's own screen handling (and a resource reload the first time), so asserting
     * the new screen on the very next tick checks too early. Re-queues at the FRONT so the following
     * commands still wait for the result.
     */
    private static void wantscreen(MinecraftClient mc, String spec) {
        var p = spec.split("\\s+");
        var expected = p[0];
        var ticks = p.length > 1 ? Integer.parseInt(p[1]) : 100;
        if (screenName().equals(expected)) {
            ok("screen " + expected);
            return;
        }
        if (ticks <= 0) {
            fail("screen " + expected, "was " + screenName());
            return;
        }
        PENDING.addFirst("wantscreen " + expected + " " + (ticks - 1));
    }

    private static String screenName() {
        var s = MinecraftClient.getInstance().currentScreen;
        return s == null ? "null" : s.getClass().getSimpleName();
    }

    private static void dumpState() {
        var a = accessor();
        if (a == null) {
            log("STATE screen=" + screenName() + " (no casting UI)");
            return;
        }
        log("STATE screen=" + screenName() + " draw=" + a.state().getClass().getSimpleName()
                + " debug=" + a.state().getDebugInfo());
    }

    private static void hints() {
        var a = accessor();
        if (a == null) {
            fail("hints", "no casting UI");
            return;
        }
        log("HINTS " + a.state().getClass().getSimpleName() + " " + a.state().getHints());
    }

    private static void entries(String filter) {
        var all = PatternEntries.INSTANCE.get();
        var hit = all.stream()
                .filter(e -> filter.isEmpty() || e.id().contains(filter))
                .limit(8)
                .map(PatternEntries.Entry::id)
                .toList();
        log("ENTRIES total=" + all.size() + " filter='" + filter + "' " + hit);
        check(!all.isEmpty(), "entries populated", "action registry empty");
    }

    private static void perworld() {
        var all = PatternEntries.INSTANCE.get();
        var pw = all.stream().filter(PatternEntries.Entry::isPerWorld).toList();
        log("PERWORLD count=" + pw.size() + " ids="
                + pw.stream().limit(8).map(PatternEntries.Entry::id).toList());
        var withImpls = all.stream().filter(e -> !e.impls().isEmpty()).count();
        log("BOOKLINK entriesWithDocs=" + withImpls + "/" + all.size());
        check(!all.isEmpty(), "perworld scan", "no entries");
    }

    /**
     * Looks up a pattern's Hex Book page.
     * <p>
     * Syntax: {@code books <op_id>[@<entry_id>][=<page>]}. The optional {@code @entry} qualifier
     * matters because the same op id can appear on a pattern page in several book entries (Hex
     * Casting has both a math and a strings page for some ops, and addons add more), and
     * {@code getBookEntryFor} returns whichever entry it indexed first. Asserting a bare page number
     * would then depend on the installed mod set rather than on correctness.
     * <p>
     * The page itself must be the pattern's <em>absolute</em> page within its entry: Patchouli
     * divides the number by two to pick a spread, so a pattern's ordinal among pattern pages is not
     * a valid page number.
     */
    private static void books(String arg) {
        var expectPage = -1;
        var eq = arg.indexOf('=');
        if (eq >= 0) {
            expectPage = Integer.parseInt(arg.substring(eq + 1));
            arg = arg.substring(0, eq);
        }
        var expectEntry = (String) null;
        var at = arg.indexOf('@');
        if (at >= 0) {
            expectEntry = arg.substring(at + 1);
            arg = arg.substring(0, at);
        }
        var id = arg;

        // The same op id can be documented on pattern pages in more than one book entry (Hex
        // Casting itself documents `hexcasting:add` for both numbers and strings), so search every
        // indexed page for this op rather than taking whichever one was indexed first.
        var all = BookEntries.INSTANCE.get(Identifier.tryParse(id));
        if (all.isEmpty()) {
            fail("book entry for " + id, "no page documents this op id");
            return;
        }
        final var wantEntry = expectEntry;
        var entry = wantEntry == null
                ? all.get(0)
                : all.stream().filter(e -> wantEntry.equals(e.entryid().toString()))
                        .findFirst().orElse(null);
        if (entry == null) {
            fail("book entry for " + id + " in " + wantEntry,
                    "not found; documented in " + all.stream()
                            .map(e -> e.entryid().toString()).distinct().toList());
            return;
        }
        log("BOOKS " + id + " entry=" + entry.entryid() + " page=" + entry.page()
                + " args='" + entry.getArgs() + "'");
        if (expectPage >= 0)
            check(entry.page() == expectPage,
                    "book page for " + id + " in " + entry.entryid() + " is " + expectPage,
                    "was " + entry.page());
        else
            ok("book entry for " + id + " in " + entry.entryid());
    }

    private static void cfg(String kv) {
        try {
            var p = kv.split("=", 2);
            var f = Hexcessible.cfg().getClass().getField(p[0]);
            if (p.length == 1) {
                log("CFG " + p[0] + " = " + f.get(Hexcessible.cfg()));
                return;
            }
            var cur = f.get(Hexcessible.cfg());
            if (cur instanceof Boolean)
                f.set(Hexcessible.cfg(), Boolean.parseBoolean(p[1]));
            else if (cur instanceof Integer)
                f.set(Hexcessible.cfg(), Integer.parseInt(p[1]));
            else if (cur instanceof String)
                f.set(Hexcessible.cfg(), p[1]);
            else if (cur instanceof Enum<?> e) {
                // Lets the KeyDocs OFF/IDLING/ALWAYS modes be driven from the script.
                @SuppressWarnings({ "unchecked", "rawtypes" })
                var value = Enum.valueOf((Class<? extends Enum>) e.getDeclaringClass(), p[1]);
                f.set(Hexcessible.cfg(), value);
            } else {
                fail("cfg " + kv, "unsupported type " + cur.getClass());
                return;
            }
            Hexcessible.cfg().markDirty();
            ok("cfg " + p[0] + "=" + f.get(Hexcessible.cfg()));
        } catch (Throwable t) {
            fail("cfg " + kv, t.toString());
        }
    }

    /** See {@link SelfTestAccess}: the server-confirmed pattern list, or null without a cast UI. */
    private static List<ResolvedPattern> resolvedPatterns() {
        var s = MinecraftClient.getInstance().currentScreen;
        return s instanceof SelfTestAccess a ? a.hexcessible$selftestPatterns() : null;
    }

    /**
     * Asserts a pattern is in the casting UI's server-confirmed list. That list is only ever
     * repopulated from {@code MsgNewSpellPatternS2C}, so an entry proves the client sent the pattern
     * and the server resolved it.
     */
    private static void haspacket(String sig) {
        var patterns = resolvedPatterns();
        if (patterns == null) {
            fail("haspacket " + sig, "no casting UI (screen=" + screenName() + ")");
            return;
        }
        var all = patterns.stream().map(p -> Utils.angle(p.getPattern().getAngles())).toList();
        check(all.contains(sig), "haspacket " + sig, "not resolved; have " + all);
    }

    /** Casts by typing the signature into the keyboard UI and pressing enter, then exits. */
    private static void cast(MinecraftClient mc, String sig) {
        if (accessor() == null) {
            fail("cast " + sig, "no casting UI");
            return;
        }
        chars(mc, sig);
        key(mc, "enter");
        ok("cast " + sig);
    }

    /**
     * Records the config screen for whichever platform this is, without a compile-time dependency on
     * either: NeoForge publishes it through the {@code ConfigScreenHandler} extension point, Fabric
     * through a ModMenu entrypoint. Neither is reachable from the shared source set, so both are
     * probed reflectively and only the one that exists is asserted.
     */
    private static void configScreen() {
        try {
            var csh = Class.forName("net.minecraftforge.client.ConfigScreenHandler");
            var modList = Class.forName("net.minecraftforge.fml.ModList");
            var ml = modList.getMethod("get").invoke(null);
            var opt = modList.getMethod("getModContainerById", String.class).invoke(ml, "hexcessible");
            var container = opt.getClass().getMethod("get").invoke(opt);
            var info = container.getClass().getMethod("getModInfo").invoke(container);
            var factory = csh.getMethod("getScreenFactoryFor",
                    Class.forName("net.minecraftforge.forgespi.language.IModInfo")).invoke(null, info);
            var present = (Boolean) factory.getClass().getMethod("isPresent").invoke(factory);
            check(present, "neoforge config screen registered", "extension point returned empty");
            return;
        } catch (ClassNotFoundException e) {
            // not NeoForge; fall through to the Fabric check
        } catch (Throwable t) {
            fail("neoforge config screen registered", t.toString());
            return;
        }

        try {
            var cls = Class.forName("dev.tizu.hexcessible.fabric.HexcessibleModMenu");
            var instance = cls.getDeclaredConstructor().newInstance();
            var factory = cls.getMethod("getModConfigScreenFactory").invoke(instance);
            check(factory != null, "fabric config screen factory", "null");
        } catch (ClassNotFoundException e) {
            fail("config screen", "neither NeoForge nor Fabric config entrypoint found");
        } catch (Throwable t) {
            fail("fabric config screen factory", t.toString());
        }
    }

    /**
     * Asserts the mixins that have to be present actually applied.
     * <p>
     * Each of these injects into a class the mod does not own, so "applied" is observable as the
     * target class carrying something vanilla does not have. This is the check that would have
     * caught an interface-targeted mixin failing to load.
     * <p>
     * Class references are used directly rather than {@code Class.forName("...")}: source-level
     * names are Yarn and get remapped per platform by Loom, whereas a string literal would not be,
     * and the NeoForge runtime carries Mojang names.
     */
    private static void mixins() {
        check(DrawStateMixinAccessor.class
                .isAssignableFrom(at.petrak.hexcasting.client.gui.GuiSpellcasting.class),
                "mixin applied: DrawStateMixin -> GuiSpellcasting",
                "GuiSpellcasting does not implement DrawStateMixinAccessor");

        // NoHexicalWalkMixin / NoHexicalEvokeMixin inject into KeyBinding; if their injections were
        // bad this class would fail to initialise.
        check(loads(net.minecraft.client.option.KeyBinding.class),
                "mixin target loads: KeyBinding", "class failed to initialise");

        // DrawStateCharTypedMixin injects into Keyboard.onChar — the retarget that replaced the
        // interface-targeted mixer.
        check(loads(net.minecraft.client.Keyboard.class),
                "mixin target loads: Keyboard", "class failed to initialise");

        // SelfTestAccessorMixin applies the resolved-pattern accessor to the casting screen.
        check(SelfTestAccess.class
                .isAssignableFrom(at.petrak.hexcasting.client.gui.GuiSpellcasting.class),
                "mixin applied: SelfTestAccessorMixin -> GuiSpellcasting",
                "GuiSpellcasting does not implement SelfTestAccess");
    }

    /**
     * Forces class initialisation and reports whether it succeeded. A class whose mixin was applied
     * incorrectly fails here, so touching the target is the assertion.
     */
    private static boolean loads(Class<?> type) {
        try {
            return type.getDeclaredMethods().length >= 0;
        } catch (Throwable t) {
            Hexcessible.LOGGER.error("HEXTEST class load failed: {}", type, t);
            return false;
        }
    }

    // ------------------------------------------------------------ UI plumbing

    private static void move(MinecraftClient mc, String xy) {
        var s = mc.currentScreen;
        if (s == null) {
            fail("move " + xy, "no screen");
            return;
        }
        var p = xy.split(",");
        s.mouseMoved(Double.parseDouble(p[0]), Double.parseDouble(p[1]));
    }

    private static void click(MinecraftClient mc, String spec) {
        var s = mc.currentScreen;
        if (s == null) {
            fail("click " + spec, "no screen");
            return;
        }
        var p = spec.split(",");
        s.mouseClicked(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Integer.parseInt(p[2]));
    }

    private static void scroll(MinecraftClient mc, String spec) {
        var s = mc.currentScreen;
        if (s == null) {
            fail("scroll " + spec, "no screen");
            return;
        }
        var p = spec.split(",");
        s.mouseScrolled(Double.parseDouble(p[0]), Double.parseDouble(p[1]),
                Double.parseDouble(p[2]));
    }

    /**
     * Draws a pattern by pressing at the first hex coordinate and dragging through the rest, using
     * the casting screen's own coordinate conversion. This is the mouse-drawing path: it goes
     * through the same mixins as a human drag.
     * <p>
     * Argument: pairs of {@code q,r} coordinates, e.g. {@code hexdrag 0,0 1,0 1,1}.
     */
    private static void hexdrag(MinecraftClient mc, String arg) {
        var screen = mc.currentScreen;
        if (!(screen instanceof GuiSpellcasting cast)) {
            fail("hexdrag", "no casting UI (screen=" + screenName() + ")");
            return;
        }
        var parts = arg.split("\\s+");
        if (parts.length < 2) {
            fail("hexdrag", "need at least two coordinates");
            return;
        }
        Vec2f prev = null;
        for (var i = 0; i < parts.length; i++) {
            var qr = parts[i].split(",");
            var px = cast.coordToPx(new HexCoord(Integer.parseInt(qr[0]), Integer.parseInt(qr[1])));
            if (i == 0) {
                screen.mouseMoved(px.x, px.y);
                screen.mouseClicked(px.x, px.y, 0);
            } else {
                // several steps so the screen sees the cursor cross each hex, as a real drag would
                for (var s = 1; s <= 4; s++) {
                    var x = prev.x + (px.x - prev.x) * s / 4.0;
                    var y = prev.y + (px.y - prev.y) * s / 4.0;
                    screen.mouseDragged(x, y, 0, x - prev.x, y - prev.y);
                }
                screen.mouseReleased(px.x, px.y, 0);
            }
            prev = px;
        }
        ok("hexdrag " + arg);
    }

    /**
     * Types characters one per tick, which is what a human does and what the state machine expects.
     * <p>
     * Sending them all in one tick would be wrong: {@code Idling.onCharType} records the first
     * character in {@code nextState} and only switches state on the next render, so the rest of the
     * line would still be delivered to {@code Idling} and each would overwrite {@code nextState} —
     * leaving only the final character applied. The remainder is re-queued at the front so the
     * characters consume consecutive ticks but still precede the next script line.
     */
    private static void chars(MinecraftClient mc, String s) {
        if (mc.currentScreen == null) {
            fail("chars " + s, "no screen");
            return;
        }
        if (s.isEmpty())
            return;
        DrawStateInput.typeChar(mc, s.charAt(0));
        if (s.length() > 1)
            PENDING.addFirst("chars " + s.substring(1));
    }

    private static void key(MinecraftClient mc, String spec) {
        var screen = mc.currentScreen;
        if (screen == null) {
            fail("key " + spec, "no screen");
            return;
        }
        var p = spec.split(",");
        var code = keyCode(p[0]);
        var mods = p.length > 1 ? Integer.parseInt(p[1]) : 0;
        screen.keyPressed(code, 0, mods);
    }

    /**
     * Resolves a key name to its GLFW code.
     * <p>
     * A few friendly aliases are accepted, and anything else is looked up through
     * {@link InputUtil}, so the script can name any key the game knows (e.g. {@code b}, {@code n},
     * {@code key.keyboard.b}) rather than only the handful this switch once hardcoded — that
     * limitation showed up as {@code unknown key b} when testing a re-bound hotkey.
     */
    private static int keyCode(String name) {
        return switch (name) {
            case "enter" -> GLFW.GLFW_KEY_ENTER;
            case "tab" -> GLFW.GLFW_KEY_TAB;
            case "space" -> GLFW.GLFW_KEY_SPACE;
            case "escape" -> GLFW.GLFW_KEY_ESCAPE;
            case "backspace" -> GLFW.GLFW_KEY_BACKSPACE;
            case "up" -> GLFW.GLFW_KEY_UP;
            case "down" -> GLFW.GLFW_KEY_DOWN;
            case "left" -> GLFW.GLFW_KEY_LEFT;
            case "right" -> GLFW.GLFW_KEY_RIGHT;
            default -> {
                var translationKey = name.contains(".") ? name : "key.keyboard." + name.toLowerCase();
                var bound = InputUtil.fromTranslationKey(translationKey);
                if (bound == InputUtil.UNKNOWN_KEY)
                    throw new IllegalArgumentException("unknown key " + name);
                yield bound.getCode();
            }
        };
    }
}
