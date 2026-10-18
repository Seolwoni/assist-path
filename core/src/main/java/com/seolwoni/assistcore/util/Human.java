package com.seolwoni.assistcore.util;

import java.util.Random;

/** Human-looking randomness: clustered delays with an occasional slow reaction. */
public final class Human {
	public static final Random RANDOM = new Random();

	/**
	 * Whether delays use the human-like distribution. Each mod sets this from its own setting
	 * at the start of its tick (all mods tick on the same thread, one after another).
	 */
	public static boolean humanLike = true;

	/** Work pace chosen in a mod's settings: 0 = relaxed (slowest, most human), 1 = normal, 2 = fast. */
	public static int pace = 0;

	private static final double[] DELAY_SCALE = {1.0, 0.6, 0.3};
	private static final float[] TURN_SCALE = {1.0f, 1.25f, 1.6f};

	/** How much faster heads turn at the current pace. */
	public static float turnScale() {
		return TURN_SCALE[Math.max(0, Math.min(2, pace))];
	}

	private Human() {
	}

	public static int rand(int min, int max) {
		if (max <= min) return min;
		return min + RANDOM.nextInt(max - min + 1);
	}

	/** A reaction delay in ticks, shortened by the work pace. */
	public static int delay(int min, int max) {
		double scale = DELAY_SCALE[Math.max(0, Math.min(2, pace))];
		if (scale >= 1.0) return rawDelay(min, max);
		return (int) Math.round(rawDelay(min, max) * scale);
	}

	private static int rawDelay(int min, int max) {
		if (max <= min) return min;
		if (!humanLike) return rand(min, max);
		double span = max - min;
		double v = RANDOM.nextDouble() < 0.08
				? min + span * (0.6 + 0.4 * RANDOM.nextDouble())
				: min + span * 0.3 + RANDOM.nextGaussian() * span * 0.15;
		return (int) Math.max(min, Math.min(max, Math.round(v)));
	}

	/** A small random offset (in blocks) for aim points, so the crosshair never lands on the exact centre. */
	public static double jitter(double amount) {
		return (RANDOM.nextDouble() * 2 - 1) * amount;
	}
}
