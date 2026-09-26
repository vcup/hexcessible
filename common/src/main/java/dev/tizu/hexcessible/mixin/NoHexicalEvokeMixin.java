package dev.tizu.hexcessible.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import dev.tizu.hexcessible.Hexcessible;
import dev.tizu.hexcessible.accessor.DrawStateMixinAccessor;
import dev.tizu.hexcessible.drawstate.Idling;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

@Mixin(KeyBinding.class)
public class NoHexicalEvokeMixin {
    @Unique
    private static final List<String> DISALLOWED = List.of(
            "key.hexical.evoke", "key.hexical.telepathy");

    // https://github.com/miyucomics/hexical/blob/main/src/client/java/miyucomics/hexical/inits/HexicalKeybinds.kt
    @Inject(method = "setPressed", at = @At("HEAD"), cancellable = true)
    private void blockPressedWhileCasting(boolean pressed, CallbackInfo ci) {
        if (!Hexcessible.cfg().noHexicalEvoke)
            return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof GuiSpellcasting castui))
            return;
        var accessor = (DrawStateMixinAccessor) (Object) castui;
        if (accessor.state() instanceof Idling)
            return;
        KeyBinding self = (KeyBinding) (Object) this;
        String id = self.getTranslationKey();
        if (DISALLOWED.contains(id) && pressed)
            ci.cancel();
    }
}
