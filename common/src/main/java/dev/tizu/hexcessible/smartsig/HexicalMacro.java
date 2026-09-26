package dev.tizu.hexcessible.smartsig;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import at.petrak.hexcasting.api.casting.math.HexAngle;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import dev.tizu.hexcessible.Utils;
import dev.tizu.hexcessible.entries.PatternEntries;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class HexicalMacro implements SmartSig.Conditional {

    @Override
    public boolean enabled() {
        return IXplatAbstractions.INSTANCE.isModPresent("hexical");
    }

    @Override
    public @Nullable List<PatternEntries.Entry> get(String query) {
        return getAllMacros().stream().map(m -> getFor(Utils.angle(m))).toList();
    }

    @Override
    public @Nullable PatternEntries.Entry get(List<HexAngle> sig) {
        var all = getAllMacros();
        for (var macro : all) {
            if (macro.equals(Utils.angle(sig)))
                return getFor(sig);
        }
        return null;
    }

    private List<String> getAllMacros() {
        var player = MinecraftClient.getInstance().player;
        var inventory = player.getInventory();

        var targetItem = Registries.ITEM.get(Identifier.of("hexical", "grimoire"));
        var stacks = Stream.of(inventory.main, inventory.offHand, inventory.armor,
                player.getEnderChestInventory().stacks)
                .flatMap(Collection::stream)
                .filter(stack -> stack.isOf(targetItem))
                .toList();
        return stacks.stream()
                .map(ItemStack::getNbt)
                .filter(Objects::nonNull)
                .map(nbt -> nbt.getCompound("expansions"))
                .flatMap(nbt -> nbt.getKeys().stream())
                .toList();
    }

    private PatternEntries.Entry getFor(List<HexAngle> sig) {
        var i18nkey = Text.translatable("hexcessible.smartsig.grimoire").getString();
        return new PatternEntries.Entry("hexical:grimoire_macro/" + Utils.angle(sig),
                i18nkey, () -> false, HexDir.EAST, List.of(sig), List.of(), 0);
    }
}
