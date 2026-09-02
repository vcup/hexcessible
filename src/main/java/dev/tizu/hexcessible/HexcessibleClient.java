package dev.tizu.hexcessible;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class HexcessibleClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			var url = "https://g.tizu.dev/hexcessible/@issues";
			client.player.sendMessage(
				Text.empty()
					.append(
						Text.literal("[!] ").setStyle(
							Style.EMPTY.withColor(Formatting.RED)
						)
					)
					.append("This is an experimental build of ")
					.append(
						Text.literal("Hexcessible").setStyle(
							Style.EMPTY.withColor(Formatting.LIGHT_PURPLE)
						)
					)
					.append(". Please report any issues you encounter ")
					.append(
						Text.literal("here").setStyle(
							Style.EMPTY.withColor(Formatting.BLUE)
								.withUnderline(true)
								.withClickEvent(
									new ClickEvent(
										ClickEvent.Action.OPEN_URL,
										url
									)
								)
						)
					)
					.append(Text.literal(".")),
				false
			);
		});
	}
}
