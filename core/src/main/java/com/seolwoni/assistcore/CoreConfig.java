package com.seolwoni.assistcore;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/** Settings shared by all Assist mods (config/assistcore.json). */
public class CoreConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("assistcore.json");
	private static CoreConfig instance = new CoreConfig();

	/** Every Assist HUD box is hidden (toggled with H). */
	public boolean hudHidden = false;
	/** Keep a plain-text usage log in assist-logs/ (what each mod did, why it stopped). Nothing is sent anywhere. */
	public boolean usageLog = true;
	/** Delete usage logs older than this many days. */
	public int usageLogDays = 30;

	public static CoreConfig get() {
		return instance;
	}

	public static void load() {
		try {
			if (Files.exists(PATH)) {
				try (Reader r = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
					CoreConfig c = GSON.fromJson(r, CoreConfig.class);
					if (c != null) instance = c;
				}
			}
		} catch (Exception e) {
			AssistCore.LOGGER.warn("Failed to load config", e);
		}
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer w = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, w);
			}
		} catch (Exception e) {
			AssistCore.LOGGER.warn("Failed to save config", e);
		}
	}
}
