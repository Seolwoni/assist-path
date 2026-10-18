package com.seolwoni.assistcore;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.InputConstants;
import com.seolwoni.assistcore.hud.AssistHud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Assist Core: the shared base of the Assist series. Made by seolwoni. */
public class AssistCore implements ClientModInitializer {
	public static final String MOD_ID = "assistcore";
	public static final Logger LOGGER = LoggerFactory.getLogger("Assist Core");

	public static KeyMapping toggleHudKey;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID).map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
	}

	@Override
	public void onInitializeClient() {
		CoreConfig.load();
		KeyMapping.Category category = KeyMapping.Category.register(id("main"));
		toggleHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.assistcore.toggle_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, category));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleHudKey.consumeClick()) AssistHud.toggleHidden();
		});
		HudElementRegistry.addLast(id("hud"), (graphics, delta) -> AssistHud.render(graphics));
		LOGGER.info("Assist Core {} loaded - made by seolwoni", version());
	}
}
