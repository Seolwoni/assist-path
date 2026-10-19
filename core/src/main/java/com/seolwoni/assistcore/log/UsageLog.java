package com.seolwoni.assistcore.log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.seolwoni.assistcore.CoreConfig;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Plain-text usage log for the Assist mods: when a job started and ended, what it did, why it stopped, what it
 * skipped. One file per mod and day in {@code assist-logs/<mod>/<date>.txt} next to the game's own logs. It stays on
 * this computer; the player can send it in to help find problems. Can be switched off (assistcore.json: usageLog).
 */
public final class UsageLog {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final Map<String, Long> started = new HashMap<>();
	private static boolean cleaned;

	private UsageLog() {
	}

	private static Path dir(String mod) {
		return FabricLoader.getInstance().getGameDir().resolve("assist-logs").resolve(mod);
	}

	/** One line, with the time and the player's position. */
	public static void event(String mod, String text) {
		if (!CoreConfig.get().usageLog) return;
		try {
			Path d = dir(mod);
			Files.createDirectories(d);
			cleanOld(d.getParent());
			String where = "";
			LocalPlayer p = Minecraft.getInstance().player;
			if (p != null) where = " @" + p.blockPosition().getX() + "," + p.blockPosition().getY() + "," + p.blockPosition().getZ();
			String line = LocalDateTime.now().format(TIME) + where + "  " + text + System.lineSeparator();
			Files.writeString(d.resolve(LocalDate.now() + ".txt"), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException | RuntimeException ignored) {
			// A log must never break the game.
		}
	}

	/** A job starts (remembered so the end line can say how long it ran). */
	public static void start(String mod, String what) {
		started.put(mod, System.currentTimeMillis());
		event(mod, "▶ 시작: " + what);
	}

	/** A job ends, with the reason and a short summary of what it did. */
	public static void end(String mod, String reason, String summary) {
		Long t0 = started.remove(mod);
		String took = t0 == null ? "" : " (" + duration(System.currentTimeMillis() - t0) + ")";
		event(mod, "■ 끝" + took + ": " + reason + (summary == null || summary.isEmpty() ? "" : " | " + summary));
	}

	public static String duration(long ms) {
		long s = ms / 1000;
		return s >= 3600 ? String.format("%d시간 %d분", s / 3600, s / 60 % 60) : String.format("%d분 %d초", s / 60, s % 60);
	}

	private static void cleanOld(Path root) {
		if (cleaned) return;
		cleaned = true;
		LocalDate limit = LocalDate.now().minusDays(Math.max(1, CoreConfig.get().usageLogDays));
		try (Stream<Path> files = Files.walk(root, 2)) {
			files.filter(f -> f.getFileName().toString().endsWith(".txt")).forEach(f -> {
				try {
					LocalDate d = LocalDate.parse(f.getFileName().toString().replace(".txt", ""));
					if (d.isBefore(limit)) Files.deleteIfExists(f);
				} catch (Exception ignored) {
				}
			});
		} catch (IOException ignored) {
		}
	}
}
