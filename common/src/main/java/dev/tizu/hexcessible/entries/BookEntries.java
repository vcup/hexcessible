package dev.tizu.hexcessible.entries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonSyntaxException;

import dev.tizu.hexcessible.Hexcessible;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import vazkii.patchouli.common.book.BookRegistry;

/**
 * This reads the Patchouli Hex Book by reading the JSON. This may be improved
 * if I figure out how to use the Patchouli API to do this.
 */
public class BookEntries {

    public static final Identifier BOOKID = Identifier.of("hexcasting", "thehexbook");
    public static final BookEntries INSTANCE = new BookEntries();

    private Map<String, List<Entry>> entries = Map.of();
    private Map<String, Supplier<Boolean>> locked = Map.of();
    /** Whether {@link #reindex()} has run against an actually-loaded book. */
    private boolean loaded = false;

    private BookEntries() {
        reindex();
    }

    /**
     * Indexes the book, provided Patchouli has loaded it.
     * <p>
     * The book is built as part of the client's resource reload, which can finish <em>after</em> the
     * first query (this class is constructed from {@code PatternEntries}' initializer, which runs on
     * first use). An earlier version logged an error and returned, leaving the index permanently
     * empty, so a query arriving too early disabled the pattern-to-book link for the whole session.
     * {@link #loaded} therefore stays false on a miss and {@link #ensureLoaded()} retries later.
     */
    public void reindex() {
        var book = BookRegistry.INSTANCE.books.get(BOOKID);
        if (book == null) {
            Hexcessible.LOGGER.debug("Book {} not loaded yet; will retry on next query", BOOKID);
            return;
        }

        var entries = new HashMap<String, List<Entry>>();
        var locked = new HashMap<String, Supplier<Boolean>>();
        book.getContents().entries.forEach((entryid, entry) -> {
            // Position within the pages Patchouli currently exposes, used only as a fallback.
            var visible = new AtomicInteger(0);
            entry.getPages().forEach(page -> {
                var visibleIndex = visible.getAndIncrement();
                var root = page.sourceObject;
                if (root == null)
                    return;
                try {
                    if (!"hexcasting:pattern".equals(JsonHelper.getString(root, "type")))
                        return;
                    var id = JsonHelper.getString(root, "op_id");
                    if (!locked.containsKey(id))
                        locked.put(id, entry::isLocked);
                    var desc = JsonHelper.getString(root, "text", "");
                    var in = JsonHelper.getString(root, "input", "");
                    var out = JsonHelper.getString(root, "output", "");
                    // The page number handed to Patchouli is not "the Nth pattern page" but the
                    // index Patchouli itself resolves from an anchor: setTopEntry divides it by two
                    // to find the spread. A pattern page's anchor is its op id, so Patchouli's own
                    // lookup is authoritative — it accounts for pages hidden by advancement gating,
                    // which a hand-rolled counter cannot. Only the raw count is used as a fallback
                    // if the anchor cannot be resolved.
                    var anchored = entry.getPageFromAnchor(id);
                    var pageNo = anchored >= 0 ? anchored : visibleIndex;
                    entries.computeIfAbsent(id, k -> new ArrayList<>())
                            .add(new Entry(id, entryid, desc, in, out, pageNo));
                } catch (JsonSyntaxException e) {
                    Hexcessible.LOGGER.error("Failed to parse entry {}", entryid, e);
                }
            });
        });
        this.entries = entries;
        this.locked = locked;
        this.loaded = true;
    }

    /** Indexes the book on first successful access, retrying until Patchouli has it. */
    private void ensureLoaded() {
        if (!loaded)
            reindex();
    }

    public static record Entry(String id, @Nullable Identifier entryid,
            String desc, String in, String out, int page) {
        public String getArgs() {
            return (in + " -> " + out).strip();
        }

        public String getDesc() {
            return Text.translatable(desc).getString()
                    .replaceAll("\\$\\([^)]*\\)|/\\$", "")
                    .replaceAll("[\\s^]_", " ");
        }
    }

    public List<Entry> get(Identifier id) {
        ensureLoaded();
        return entries.getOrDefault(id.toString(), List.of());
    }

    public boolean isLocked(String id) {
        ensureLoaded();
        return locked.getOrDefault(id, () -> false).get();
    }

    @Nullable
    public Entry getBookEntryFor(String id) {
        ensureLoaded();
        return entries.getOrDefault(id, List.of()).stream().findFirst().orElse(null);
    }
}
