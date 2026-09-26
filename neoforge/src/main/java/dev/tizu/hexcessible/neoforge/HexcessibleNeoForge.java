package dev.tizu.hexcessible.neoforge;

import dev.tizu.hexcessible.Hexcessible;
import dev.tizu.hexcessible.HexcessibleConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * The NeoForge mod entrypoint.
 * <p>
 * Hexcessible is client-only and almost entirely mixin-driven, so this class has one job: publish the
 * config screen to NeoForge. Fabric gets that from a ModMenu entrypoint; NeoForge's equivalent is the
 * {@code ConfigScreenHandler} extension point, which is what puts a "Config" button on the mod list
 * entry. The screen is the same Cloth Config/AutoConfig one on both platforms.
 */
@Mod("hexcessible")
public class HexcessibleNeoForge {
    public HexcessibleNeoForge() {
        // A mod file is loaded on both sides even when it is client-only, and this API (and the
        // AutoConfig screen below it) does not exist on a dedicated server.
        if (FMLEnvironment.dist != Dist.CLIENT)
            return;
        // Build the config now, exactly as the Fabric entrypoint does, so a config file is written
        // even when the screen is never opened.
        Hexcessible.cfg();
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> AutoConfig
                        .getConfigScreen(HexcessibleConfig.class, parent).get()));
    }
}
