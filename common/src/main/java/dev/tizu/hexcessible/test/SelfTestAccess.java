package dev.tizu.hexcessible.test;

import java.util.List;

import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;

/**
 * Reads the casting UI's server-confirmed pattern list.
 * <p>
 * The self-test needs to know which patterns the server actually resolved, which is the only
 * observable proof that a cast round-tripped. {@code patterns} is a private field on Hex Casting's
 * screen, so it is exposed through the accessor mixin in this same (self-test only) package rather
 * than by reaching into it reflectively — a {@code @Unique} field injected by our own mixin has a
 * loader-dependent name, whereas this field is Hex Casting's own and stable.
 */
public interface SelfTestAccess {
    List<ResolvedPattern> hexcessible$selftestPatterns();
}
