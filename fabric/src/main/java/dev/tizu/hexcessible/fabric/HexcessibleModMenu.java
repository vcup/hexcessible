package dev.tizu.hexcessible.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import dev.tizu.hexcessible.Hexcessible;
import dev.tizu.hexcessible.HexcessibleConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * The Fabric config-screen entrypoint, hosted by ModMenu.
 * <p>
 * The screen itself is Cloth Config's, built from {@link HexcessibleConfig} by AutoConfig; ModMenu is
 * only the thing that offers a button for it. NeoForge has no equivalent entrypoint, so the same
 * screen is registered there through Forge's {@code ConfigScreenHandler} extension point instead —
 * see {@code HexcessibleNeoForge}.
 */
@Environment(EnvType.CLIENT)
public class HexcessibleModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        Hexcessible.cfg();
        return parent -> AutoConfig.getConfigScreen(HexcessibleConfig.class,
                parent).get();
    }
}
