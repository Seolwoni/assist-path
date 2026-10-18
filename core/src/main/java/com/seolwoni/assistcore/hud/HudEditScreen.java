package com.seolwoni.assistcore.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag HUD boxes (of every Assist mod) to move them; scroll over a box or drag its yellow corner to resize it. */
public class HudEditScreen extends Screen {
	private static final int HANDLE = 6;
	private final Screen parent;
	private HudBox dragging, resizing;
	private double grabDx, grabDy;

	public HudEditScreen(Screen parent) {
		super(Component.literal("HUD 위치 편집"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.literal("완료"), b -> onClose())
				.bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		super.extractRenderState(g, mx, my, delta);
		for (HudBox box : AssistHud.boxes()) {
			if (!box.enabled()) continue;
			box.draw(g, true);
			float[] b = box.bounds();
			int hx = (int) (b[0] + b[2]) - HANDLE, hy = (int) (b[1] + b[3]) - HANDLE;
			g.fill(hx, hy, hx + HANDLE, hy + HANDLE, 0xFFFFFF55);
			g.text(this.font, Component.literal(Math.round(box.scale() * 100) + "%").getVisualOrderText(),
					(int) b[0], (int) (b[1] + b[3]) + 2, 0xFFFFFF55, true);
		}
		g.centeredText(this.font, Component.literal("드래그로 이동 · 노란 칸을 끌거나 휠로 크기 조절"), this.width / 2, 8, 0xFFFFFFFF);
	}

	private static HudBox hit(double mx, double my) {
		for (HudBox box : AssistHud.boxes()) {
			if (!box.enabled()) continue;
			float[] b = box.bounds();
			if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) return box;
		}
		return null;
	}

	private static HudBox handle(double mx, double my) {
		for (HudBox box : AssistHud.boxes()) {
			if (!box.enabled()) continue;
			float[] b = box.bounds();
			double hx = b[0] + b[2], hy = b[1] + b[3];
			if (mx >= hx - HANDLE - 2 && mx <= hx + 2 && my >= hy - HANDLE - 2 && my <= hy + 2) return box;
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (super.mouseClicked(e, dbl)) return true;
		HudBox h = handle(e.x(), e.y());
		if (h != null) {
			resizing = h;
			return true;
		}
		HudBox b = hit(e.x(), e.y());
		if (b != null) {
			float[] r = b.bounds();
			dragging = b;
			grabDx = e.x() - r[0];
			grabDy = e.y() - r[1];
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (resizing != null) {
			float[] b = resizing.bounds();
			int base = resizing.baseWidth();
			if (base > 0) resizing.setScale((float) ((e.x() - b[0]) / base));
			return true;
		}
		if (dragging != null) {
			dragging.setPos(
					(float) Math.max(0, Math.min(1, (e.x() - grabDx) / this.width)),
					(float) Math.max(0, Math.min(1, (e.y() - grabDy) / this.height)));
			return true;
		}
		return super.mouseDragged(e, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (dragging != null || resizing != null) {
			(dragging != null ? dragging : resizing).save();
			dragging = resizing = null;
			return true;
		}
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		HudBox b = hit(mx, my);
		if (b != null) {
			b.setScale(b.scale() + (sy > 0 ? 0.1f : -0.1f));
			b.save();
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public void onClose() {
		for (HudBox b : AssistHud.boxes()) b.save();
		this.minecraft.gui.setScreen(parent);
	}
}
