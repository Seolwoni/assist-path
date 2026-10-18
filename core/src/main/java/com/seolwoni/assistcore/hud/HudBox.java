package com.seolwoni.assistcore.hud;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * One movable, resizable HUD box made of coloured text lines. A mod subclasses this, says where its
 * position and size are stored, and fills in the lines; drawing, editing and hiding are shared.
 */
public abstract class HudBox {
	public static final int GOLD = 0xFFFFAA00, WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA, GREEN = 0xFF55FF55, RED = 0xFFFF5555;
	private static final int PAD = 3;

	/** A piece of text in one colour. */
	public record Seg(String t, int c) {
	}

	private int w = 140, h = 60;

	/** Shown at all (mod on and this box switched on in the settings). */
	public abstract boolean enabled();

	public abstract List<List<Seg>> lines();

	/** Position as a fraction of the screen (0..1). */
	public abstract float x();

	public abstract float y();

	public abstract float scale();

	public abstract void setPos(float x, float y);

	protected abstract void storeScale(float s);

	/** Background darkness 0..255. */
	public int bgAlpha() {
		return 110;
	}

	/** Persists position/size changes. */
	public abstract void save();

	public final void setScale(float s) {
		storeScale(Math.round(Math.max(0.5f, Math.min(3f, s)) * 100f) / 100f);
	}

	public final int baseWidth() {
		return w;
	}

	/** x, y, width, height on screen, kept inside the window. */
	public final float[] bounds() {
		Minecraft mc = Minecraft.getInstance();
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		float s = scale();
		float bw = w * s, bh = h * s;
		return new float[] {Math.max(0, Math.min(x() * sw, sw - bw)), Math.max(0, Math.min(y() * sh, sh - bh)), bw, bh};
	}

	public final void draw(GuiGraphicsExtractor g, boolean editing) {
		Font font = Minecraft.getInstance().font;
		List<List<Seg>> lines = lines();
		int lw = 0;
		for (List<Seg> l : lines) {
			int x = 0;
			for (Seg s : l) x += font.width(s.t());
			lw = Math.max(lw, x);
		}
		int lh = font.lineHeight + 1;
		w = lw + PAD * 2;
		h = lines.size() * lh + PAD * 2 - 1;
		float[] b = bounds();
		g.pose().pushMatrix();
		g.pose().translate(b[0], b[1]);
		g.pose().scale(scale(), scale());
		if (bgAlpha() > 0) g.fill(0, 0, w, h, (bgAlpha() & 0xFF) << 24);
		if (editing) g.outline(0, 0, w, h, 0xFFFFFF55);
		int y = PAD;
		for (List<Seg> l : lines) {
			int x = PAD;
			for (Seg s : l) {
				g.text(font, Component.literal(s.t()).getVisualOrderText(), x, y, s.c(), true);
				x += font.width(s.t());
			}
			y += lh;
		}
		g.pose().popMatrix();
	}
}
