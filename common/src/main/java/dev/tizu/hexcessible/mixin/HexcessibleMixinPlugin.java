package dev.tizu.hexcessible.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Guards the mixins whose target only exists when another mod is installed.
 * <p>
 * The Splicing Table is HexDebug's second casting surface, and Hexcessible hooks it so keyboard
 * drawing works there too. HexDebug is optional, and both loaders fail a mixin whose target class is
 * missing, so that mixin is skipped unless the mod is present.
 * <p>
 * Presence is asked of the loader rather than tested by loading the target class. A mixin config
 * plugin runs during class transformation, from a classloader that cannot see mod classes, so a
 * {@code Class.forName} probe reports every optional mod as absent — which silently disables the
 * interop even when the mod is installed (observed against HexDebug on NeoForge). Both loaders are
 * asked reflectively so this shared class needs no loader dependency, and each loader is only
 * touched when its own API is actually on the classpath.
 * <p>
 * This class must not import Hexcessible: that would run the client-only config initializer when the
 * plugin is constructed, including on a dedicated server. It carries its own logger instead.
 */
public class HexcessibleMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("hexcessible");

    /** Mixin -> the mod id whose presence decides whether it may be applied. */
    private static final List<OptionalMixin> OPTIONAL = List.of(
            new OptionalMixin("dev.tizu.hexcessible.mixin.DrawStateHexdbgInteropMixin", "hexdebug"));

    /** Optional mixin target classes, used only to report a genuinely missing class clearly. */
    private static final Set<String> NEVER = Set.of();

    private record OptionalMixin(String mixinClass, String modId) {
    }

    @Override
    public void onLoad(String mixinPackage) {
        // no-op
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        for (var opt : OPTIONAL) {
            if (!opt.mixinClass().equals(mixinClassName))
                continue;
            if (isModLoaded(opt.modId())) {
                LOGGER.info("Applying {} ({} is present)", mixinClassName, opt.modId());
                return true;
            }
            LOGGER.info("Skipping {}: {} is not installed", mixinClassName, opt.modId());
            return false;
        }
        return true;
    }

    /**
     * Asks whichever loader is running whether a mod id is present, without a compile-time
     * dependency on either. Returns false if neither loader can be reached, which is the safe
     * default for an optional mixin.
     */
    private static boolean isModLoaded(String modId) {
        // Fabric Loader.
        try {
            var fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader");
            var instance = fabricLoader.getMethod("getInstance").invoke(null);
            var loaded = (Boolean) instance.getClass()
                    .getMethod("isModLoaded", String.class).invoke(instance, modId);
            return loaded;
        } catch (Throwable ignored) {
            // not running under Fabric Loader
        }

        // NeoForge / Forge. The loading mod list exists from the very start of mod discovery, which
        // is what makes it usable this early; ModList itself is not populated yet.
        try {
            var fmlloader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            var loadingModList = fmlloader.getMethod("getLoadingModList").invoke(null);
            if (loadingModList == null)
                return false;
            var modFile = loadingModList.getClass()
                    .getMethod("getModFileById", String.class).invoke(loadingModList, modId);
            if (modFile != null)
                return true;
            // Fall back to the fully built list when it is available.
            var modList = Class.forName("net.minecraftforge.fml.ModList");
            var instance = modList.getMethod("get").invoke(null);
            if (instance == null)
                return false;
            return (Boolean) instance.getClass()
                    .getMethod("isLoaded", String.class).invoke(instance, modId);
        } catch (Throwable ignored) {
            // not running under NeoForge/Forge either
        }

        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // no-op
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
        // no-op
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
        // no-op
    }
}
